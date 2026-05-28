(function () {
    const views = {
        home: document.getElementById("home-view"),
        list: document.getElementById("list-view"),
        lobby: document.getElementById("lobby-view"),
        game: document.getElementById("game-view")
    };

    const state = {
        lobbyId: null,
        userId: null,
        nickname: null,
        lobbyStatus: null,
        players: [],
        stompClient: null,
        subscriptions: [],
        wsState: "OFFLINE"
    };

    const sessionInfo = document.getElementById("session-info");
    const wsBadge = document.getElementById("ws-badge");
    const eventLog = document.getElementById("event-log");
    const chatLog = document.getElementById("chat-log");
    const gameLog = document.getElementById("game-log");
    const playersList = document.getElementById("players-list");
    const lobbyMeta = document.getElementById("lobby-meta");
    const lobbiesList = document.getElementById("lobbies-list");

    function now() {
        return new Date().toLocaleTimeString();
    }

    function appendLog(element, message, payload) {
        const line = `[${now()}] ${message}`;
        element.textContent += payload ? `${line}\n${JSON.stringify(payload, null, 2)}\n\n` : `${line}\n`;
        element.scrollTop = element.scrollHeight;
    }

    function logEvent(message, payload) {
        appendLog(eventLog, message, payload);
    }

    function logChat(message, payload) {
        appendLog(chatLog, message, payload);
    }

    function logGame(message, payload) {
        appendLog(gameLog, message, payload);
    }

    function updateWsBadge(value) {
        state.wsState = value;
        wsBadge.textContent = `WS: ${value}`;
        wsBadge.className = "badge";
        if (value === "CONNECTED") {
            wsBadge.classList.add("connected");
            return;
        }
        if (value === "CONNECTING" || value === "RECONNECTING") {
            wsBadge.classList.add("connecting");
            return;
        }
        wsBadge.classList.add("offline");
    }

    function updateSessionInfo() {
        if (!state.lobbyId) {
            sessionInfo.textContent = "Нет активной сессии";
            lobbyMeta.textContent = "Лобби не выбрано";
            renderPlayers();
            return;
        }

        sessionInfo.textContent =
            `lobbyId=${state.lobbyId} | userId=${state.userId} | nickname=${state.nickname}`;
        lobbyMeta.textContent =
            `Лобби: ${state.lobbyId} | статус: ${state.lobbyStatus || "unknown"} | игроков: ${state.players.length}`;
    }

    function renderPlayers() {
        if (!state.players.length) {
            playersList.textContent = "Пока нет данных об участниках. Нажми ready/chat или дождись события lobby-state.";
            return;
        }
        playersList.innerHTML = state.players
            .map((player) => {
                const you = player.id === state.userId ? " (вы)" : "";
                const ready = player.ready ? "ready" : "not ready";
                return `<div class="player-item">${player.nickname}${you} - ${ready}</div>`;
            })
            .join("");
    }

    function showView(viewName) {
        Object.entries(views).forEach(([name, node]) => {
            node.classList.toggle("hidden", name !== viewName);
        });
    }

    function parseRoute() {
        const raw = location.hash.replace("#", "") || "home";
        if (raw === "home") {
            return { name: "home" };
        }
        if (raw === "list") {
            return { name: "list" };
        }
        if (raw.startsWith("lobby/")) {
            return { name: "lobby", lobbyId: raw.split("/")[1] || null };
        }
        if (raw.startsWith("game/")) {
            return { name: "game", lobbyId: raw.split("/")[1] || null };
        }
        return { name: "home" };
    }

    function navigate(hash) {
        location.hash = hash;
    }

    async function renderRoute() {
        const route = parseRoute();
        showView(route.name);

        if (route.name === "list") {
            await refreshLobbyList();
        }

        if ((route.name === "lobby" || route.name === "game") && route.lobbyId && !state.lobbyId) {
            state.lobbyId = route.lobbyId;
            updateSessionInfo();
        }
    }

    async function api(url, options) {
        const response = await fetch(url, {
            credentials: "include",
            headers: {
                "Content-Type": "application/json"
            },
            ...options
        });

        const text = await response.text();
        let body = null;
        try {
            body = text ? JSON.parse(text) : null;
        } catch (error) {
            body = text;
        }

        if (!response.ok) {
            throw new Error(typeof body === "string" ? body : JSON.stringify(body));
        }
        return body;
    }

    async function createLobby(userName) {
        return api("/api/v1/lobbies", {
            method: "POST",
            body: JSON.stringify({ userName })
        });
    }

    async function listLobbies(status) {
        return api(`/api/v1/lobbies?status=${encodeURIComponent(status || "OPEN")}`, { method: "GET" });
    }

    async function joinLobby(lobbyId, nickname) {
        return api(`/api/v1/lobbies/${lobbyId}/join`, {
            method: "POST",
            body: JSON.stringify({ lobbyId, nickname })
        });
    }

    async function leaveLobby() {
        ensureSession();
        await api(`/api/v1/lobbies/${state.lobbyId}/leave`, { method: "POST" });
    }

    async function loadChatHistory() {
        ensureSession();
        return api(`/api/v1/lobbies/${state.lobbyId}/chat`, { method: "GET" });
    }

    async function clearCookie() {
        await api("/api/v1/auth/clear-cookie", { method: "POST" });
    }

    function ensureSession() {
        if (!state.lobbyId) {
            throw new Error("Сначала создайте или подключитесь к лобби");
        }
    }

    function applyAuth(auth) {
        state.lobbyId = auth.lobby.id;
        state.userId = auth.user.id;
        state.nickname = auth.user.nickname;
        state.lobbyStatus = auth.lobby.status;
        updateSessionInfo();
        document.getElementById("join-lobby-id").value = state.lobbyId;
        document.getElementById("home-lobby-id").value = state.lobbyId;
        if (state.stompClient) {
            resubscribeTopics();
        }
    }

    function resetSession() {
        state.lobbyId = null;
        state.userId = null;
        state.nickname = null;
        state.lobbyStatus = null;
        state.players = [];
        updateSessionInfo();
    }

    function createStompClient() {
        return new StompJs.Client({
            brokerURL: `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`,
            reconnectDelay: 1500,
            heartbeatIncoming: 5000,
            heartbeatOutgoing: 5000,
            onConnect: () => {
                updateWsBadge("CONNECTED");
                logEvent("WebSocket подключен");
                resubscribeTopics();
            },
            onDisconnect: () => {
                updateWsBadge("OFFLINE");
                logEvent("WebSocket отключен");
            },
            onWebSocketClose: () => {
                updateWsBadge("RECONNECTING");
            },
            onStompError: (frame) => {
                logEvent(`STOMP error: ${frame.headers.message || "unknown"}`, frame.body);
            },
            onWebSocketError: (event) => {
                logEvent("WebSocket error", String(event));
                updateWsBadge("RECONNECTING");
            }
        });
    }

    function clearSubscriptions() {
        state.subscriptions.forEach((sub) => sub.unsubscribe());
        state.subscriptions = [];
    }

    function resubscribeTopics() {
        clearSubscriptions();
        if (!state.stompClient || !state.stompClient.connected || !state.lobbyId) {
            return;
        }
        const lobbyTopic = `/topic/lobby/${state.lobbyId}`;
        const gameTopic = `/topic/game/${state.lobbyId}`;

        state.subscriptions.push(state.stompClient.subscribe(lobbyTopic, (msg) => {
            const payload = safeJsonParse(msg.body);
            handleRealtimeMessage(payload, lobbyTopic);
        }));

        state.subscriptions.push(state.stompClient.subscribe(gameTopic, (msg) => {
            const payload = safeJsonParse(msg.body);
            handleRealtimeMessage(payload, gameTopic);
        }));

        state.subscriptions.push(state.stompClient.subscribe("/user/queue/reply", (msg) => {
            const payload = safeJsonParse(msg.body);
            handleRealtimeMessage(payload, "/user/queue/reply");
        }));
        logEvent("Подписки WS обновлены", { lobbyTopic, gameTopic, userTopic: "/user/queue/reply" });
    }

    function safeJsonParse(body) {
        try {
            return JSON.parse(body);
        } catch (error) {
            return { raw: body };
        }
    }

    function handleRealtimeMessage(payload, source) {
        logEvent(`Realtime сообщение из ${source}`, payload);

        if (!payload || typeof payload !== "object") {
            return;
        }

        if (payload.command === "CLEAR_JWT") {
            handleClearJwtCommand(payload);
            return;
        }

        if (Array.isArray(payload.players)) {
            state.players = payload.players;
            renderPlayers();
            updateSessionInfo();
            return;
        }

        if (payload.message && payload.senderNickname) {
            logChat(`${payload.senderNickname}: ${payload.message}`);
            return;
        }

        if (payload.changedBy && payload.lobby) {
            state.lobbyStatus = payload.lobby.status;
            updateSessionInfo();
            return;
        }

        if (payload.startedBy && payload.lobby) {
            state.lobbyStatus = payload.lobby.status;
            updateSessionInfo();
            navigate(`game/${state.lobbyId}`);
            logGame(`Игра запущена админом ${payload.startedBy}`, payload);
            return;
        }

        if (payload.action && payload.user) {
            logGame(`Игровое действие: ${payload.action}`, payload);
            return;
        }

        if (payload.action && typeof payload.version === "number") {
            logGame(`Админское действие: ${payload.action} v${payload.version}`, payload);
        }
    }

    async function handleClearJwtCommand(payload) {
        logEvent("Получена команда очистки JWT", payload);
        disconnectWs();
        resetSession();
        try {
            await clearCookie();
        } catch (error) {
            logEvent(`Ошибка clear-cookie: ${error.message}`);
        }
        navigate("home");
    }

    function connectWs() {
        ensureSession();
        if (state.stompClient && state.stompClient.active) {
            logEvent("WebSocket уже активен");
            return;
        }
        if (!state.stompClient) {
            state.stompClient = createStompClient();
        }
        updateWsBadge("CONNECTING");
        state.stompClient.activate();
    }

    function disconnectWs() {
        clearSubscriptions();
        if (state.stompClient) {
            state.stompClient.deactivate();
            state.stompClient = null;
        }
        updateWsBadge("OFFLINE");
    }

    function sendStomp(destination, payload) {
        ensureSession();
        if (!state.stompClient || !state.stompClient.connected) {
            throw new Error("WebSocket не подключен");
        }
        state.stompClient.publish({
            destination,
            body: JSON.stringify(payload)
        });
    }

    async function refreshLobbyList() {
        lobbiesList.textContent = "Загрузка...";
        try {
            const lobbies = await listLobbies("OPEN");
            if (!lobbies.length) {
                lobbiesList.textContent = "Открытых лобби пока нет.";
                return;
            }
            lobbiesList.innerHTML = lobbies
                .map((lobby) => (
                    `<div class="lobby-item">` +
                    `<span>Код: <strong>${lobby.id}</strong> | Статус: ${lobby.status} | Игроков: ${lobby.playersCount}</span>` +
                    `<button class="small join-suggest-btn" type="button" data-lobby-id="${lobby.id}">Выбрать</button>` +
                    `</div>`
                ))
                .join("");
        } catch (error) {
            lobbiesList.textContent = `Ошибка загрузки списка: ${error.message}`;
        }
    }

    document.getElementById("go-home-btn").addEventListener("click", () => navigate("home"));
    document.getElementById("go-list-btn").addEventListener("click", () => navigate("list"));
    document.getElementById("go-lobby-btn").addEventListener("click", () => {
        if (!state.lobbyId) {
            logEvent("Сессия отсутствует, остаемся на главной");
            navigate("home");
            return;
        }
        navigate(`lobby/${state.lobbyId}`);
    });
    document.getElementById("go-game-btn").addEventListener("click", () => {
        if (!state.lobbyId) {
            logEvent("Сессия отсутствует, остаемся на главной");
            navigate("home");
            return;
        }
        navigate(`game/${state.lobbyId}`);
    });
    document.getElementById("home-open-list-btn").addEventListener("click", () => navigate("list"));

    document.getElementById("create-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            const userName = document.getElementById("create-name").value.trim();
            const auth = await createLobby(userName);
            applyAuth(auth);
            navigate(`lobby/${state.lobbyId}`);
            connectWs();
            logEvent("Лобби создано", auth.lobby);
        } catch (error) {
            logEvent(`Ошибка создания лобби: ${error.message}`);
        }
    });

    async function submitJoin(lobbyIdInput, nicknameInput) {
        const lobbyId = lobbyIdInput.value.trim();
        const nickname = nicknameInput.value.trim();
        const auth = await joinLobby(lobbyId, nickname);
        applyAuth(auth);
        navigate(`lobby/${state.lobbyId}`);
        connectWs();
        logEvent("Вход в лобби выполнен", auth.lobby);
    }

    document.getElementById("home-join-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            await submitJoin(document.getElementById("home-lobby-id"), document.getElementById("home-nickname"));
        } catch (error) {
            logEvent(`Ошибка входа в лобби: ${error.message}`);
        }
    });

    document.getElementById("join-form").addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            await submitJoin(document.getElementById("join-lobby-id"), document.getElementById("join-name"));
        } catch (error) {
            logEvent(`Ошибка входа в лобби: ${error.message}`);
        }
    });

    document.getElementById("refresh-lobbies-btn").addEventListener("click", refreshLobbyList);
    lobbiesList.addEventListener("click", (event) => {
        const button = event.target.closest(".join-suggest-btn");
        if (!button) {
            return;
        }
        const lobbyId = button.dataset.lobbyId;
        document.getElementById("join-lobby-id").value = lobbyId;
        document.getElementById("home-lobby-id").value = lobbyId;
        logEvent(`Подставлен код лобби: ${lobbyId}`);
    });

    document.getElementById("connect-ws-btn").addEventListener("click", () => {
        try {
            connectWs();
        } catch (error) {
            logEvent(`Ошибка подключения WS: ${error.message}`);
        }
    });
    document.getElementById("disconnect-ws-btn").addEventListener("click", disconnectWs);

    document.getElementById("leave-lobby-btn").addEventListener("click", async () => {
        try {
            await leaveLobby();
            disconnectWs();
            resetSession();
            navigate("home");
            logEvent("Выход из лобби выполнен");
        } catch (error) {
            logEvent(`Ошибка выхода из лобби: ${error.message}`);
        }
    });

    document.getElementById("load-chat-btn").addEventListener("click", async () => {
        try {
            const history = await loadChatHistory();
            chatLog.textContent = "";
            history.slice().reverse().forEach((entry) => {
                logChat(`${entry.senderNickname}: ${entry.message}`);
            });
            logEvent(`История чата загружена: ${history.length} сообщений`);
        } catch (error) {
            logEvent(`Ошибка загрузки истории: ${error.message}`);
        }
    });

    document.getElementById("chat-form").addEventListener("submit", (event) => {
        event.preventDefault();
        try {
            const input = document.getElementById("chat-input");
            const message = input.value.trim();
            if (!message) {
                return;
            }
            logChat(`${state.nickname || "Вы"}: ${message} (локально, отправка...)`);
            sendStomp(`/app/lobby/${state.lobbyId}/chat`, { message });
            input.value = "";
        } catch (error) {
            logEvent(`Ошибка отправки чата: ${error.message}`);
        }
    });

    document.getElementById("ready-on-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/ready`, { ready: true });
            logEvent("Отправлено ready=true");
        } catch (error) {
            logEvent(`Ошибка ready ON: ${error.message}`);
        }
    });

    document.getElementById("ready-off-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/ready`, { ready: false });
            logEvent("Отправлено ready=false");
        } catch (error) {
            logEvent(`Ошибка ready OFF: ${error.message}`);
        }
    });

    document.getElementById("status-open-btn").addEventListener("click", () => {
        try {
            state.lobbyStatus = "OPEN";
            updateSessionInfo();
            sendStomp(`/app/lobby/${state.lobbyId}/status`, { status: "OPEN" });
            logEvent("Отправлена смена статуса OPEN");
        } catch (error) {
            logEvent(`Ошибка status OPEN: ${error.message}`);
        }
    });

    document.getElementById("status-close-btn").addEventListener("click", () => {
        try {
            state.lobbyStatus = "CLOSE";
            updateSessionInfo();
            sendStomp(`/app/lobby/${state.lobbyId}/status`, { status: "CLOSE" });
            logEvent("Отправлена смена статуса CLOSE");
        } catch (error) {
            logEvent(`Ошибка status CLOSE: ${error.message}`);
        }
    });

    document.getElementById("start-game-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/start`, {});
            logEvent("Отправлена команда start game");
        } catch (error) {
            logEvent(`Ошибка start game: ${error.message}`);
        }
    });

    document.getElementById("open-characteristic-form").addEventListener("submit", (event) => {
        event.preventDefault();
        try {
            const characteristicName = document.getElementById("characteristic-name").value.trim();
            if (!characteristicName) {
                return;
            }
            sendStomp(`/app/game/${state.lobbyId}/open-characteristic`, { characteristicName });
            logGame(`Отправлено открытие характеристики: ${characteristicName}`);
        } catch (error) {
            logEvent(`Ошибка открытия характеристики: ${error.message}`);
        }
    });

    document.getElementById("shuffle-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/game/${state.lobbyId}/shuffle-cards`, {});
            logGame("Отправлена команда shuffle cards");
        } catch (error) {
            logEvent(`Ошибка shuffle cards: ${error.message}`);
        }
    });

    window.addEventListener("hashchange", () => {
        renderRoute().catch((error) => logEvent(`Ошибка маршрутизации: ${error.message}`));
    });

    updateWsBadge("OFFLINE");
    updateSessionInfo();
    if (!location.hash) {
        navigate("home");
    } else {
        renderRoute().catch((error) => logEvent(`Ошибка маршрутизации: ${error.message}`));
    }
    logEvent("SPA frontend загружен");
})();

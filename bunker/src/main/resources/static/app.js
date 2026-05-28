(function () {
    const views = {
        home: document.getElementById("home-view"),
        list: document.getElementById("list-view"),
        lobby: document.getElementById("lobby-view"),
        game: document.getElementById("game-view")
    };

    const dom = {
        goHomeBtn: document.getElementById("go-home-btn"),
        goListBtn: document.getElementById("go-list-btn"),
        goLobbyBtn: document.getElementById("go-lobby-btn"),
        goGameBtn: document.getElementById("go-game-btn"),
        homeOpenListBtn: document.getElementById("home-open-list-btn"),
        refreshLobbiesBtn: document.getElementById("refresh-lobbies-btn"),
        createForm: document.getElementById("create-form"),
        homeJoinForm: document.getElementById("home-join-form"),
        joinForm: document.getElementById("join-form"),
        joinLobbyId: document.getElementById("join-lobby-id"),
        homeLobbyId: document.getElementById("home-lobby-id"),
        homeNickname: document.getElementById("home-nickname"),
        joinName: document.getElementById("join-name"),
        leaveLobbyBtn: document.getElementById("leave-lobby-btn"),
        readyToggleBtn: document.getElementById("ready-toggle-btn"),
        statusOpenBtn: document.getElementById("status-open-btn"),
        statusCloseBtn: document.getElementById("status-close-btn"),
        startGameBtn: document.getElementById("start-game-btn"),
        loadChatBtn: document.getElementById("load-chat-btn"),
        chatForm: document.getElementById("chat-form"),
        chatInput: document.getElementById("chat-input"),
        openCharacteristicForm: document.getElementById("open-characteristic-form"),
        characteristicName: document.getElementById("characteristic-name"),
        shuffleBtn: document.getElementById("shuffle-btn"),
        lobbyAdminActions: document.getElementById("lobby-admin-actions"),
        gameAdminActions: document.getElementById("game-admin-actions")
    };

    const state = {
        lobbyId: null,
        userId: null,
        nickname: null,
        adminId: null,
        lobbyStatus: null,
        players: [],
        isReadyLocal: false,
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

    function isInSession() {
        return Boolean(state.lobbyId && state.userId);
    }

    function isAdmin() {
        return Boolean(state.adminId && state.userId && state.adminId === state.userId);
    }

    function isLobbyStage() {
        return state.lobbyStatus === "OPEN" || state.lobbyStatus === "CLOSE";
    }

    function isGameStage() {
        return state.lobbyStatus === "GAME";
    }

    function isWsConnected() {
        return state.wsState === "CONNECTED";
    }

    function setEnabled(element, enabled) {
        if (!element) {
            return;
        }
        element.disabled = !enabled;
    }

    function setFormEnabled(form, enabled) {
        if (!form) {
            return;
        }
        form.querySelectorAll("input, button, select, textarea").forEach((element) => {
            element.disabled = !enabled;
        });
    }

    function syncLocalReadyState() {
        if (!state.userId || !Array.isArray(state.players)) {
            state.isReadyLocal = false;
            return;
        }
        const me = state.players.find((player) => player.id === state.userId);
        state.isReadyLocal = Boolean(me && me.ready);
    }

    function updateReadyToggleUi() {
        dom.readyToggleBtn.textContent = state.isReadyLocal ? "Готов" : "Не готов";
        dom.readyToggleBtn.classList.remove("active", "inactive");
        dom.readyToggleBtn.classList.add(state.isReadyLocal ? "active" : "inactive");
    }

    function syncUiPermissions() {
        const inSession = isInSession();
        const admin = isAdmin();
        const lobbyStage = isLobbyStage();
        const gameStage = isGameStage();
        const wsConnected = isWsConnected();

        setEnabled(dom.goHomeBtn, !inSession);
        setEnabled(dom.goListBtn, !inSession);
        setEnabled(dom.homeOpenListBtn, !inSession);
        setEnabled(dom.refreshLobbiesBtn, !inSession);
        setFormEnabled(dom.createForm, !inSession);
        setFormEnabled(dom.homeJoinForm, !inSession);
        setFormEnabled(dom.joinForm, !inSession);

        setEnabled(dom.goLobbyBtn, inSession);
        setEnabled(dom.goGameBtn, inSession);
        setEnabled(dom.leaveLobbyBtn, inSession);

        setEnabled(dom.readyToggleBtn, inSession && lobbyStage && wsConnected);
        setEnabled(dom.loadChatBtn, inSession && lobbyStage);
        setFormEnabled(dom.chatForm, inSession && lobbyStage && wsConnected);
        setFormEnabled(dom.openCharacteristicForm, inSession && gameStage && wsConnected);

        dom.lobbyAdminActions.classList.toggle("hidden", !admin);
        dom.gameAdminActions.classList.toggle("hidden", !admin);
        setEnabled(dom.statusOpenBtn, admin && inSession && lobbyStage && wsConnected);
        setEnabled(dom.statusCloseBtn, admin && inSession && lobbyStage && wsConnected);
        setEnabled(dom.startGameBtn, admin && inSession && lobbyStage && wsConnected);
        setEnabled(dom.shuffleBtn, admin && inSession && gameStage && wsConnected);

        updateReadyToggleUi();
    }

    function updateWsBadge(value) {
        state.wsState = value;
        wsBadge.textContent = `WS: ${value}`;
        wsBadge.className = "badge";
        if (value === "CONNECTED") {
            wsBadge.classList.add("connected");
        } else if (value === "CONNECTING" || value === "RECONNECTING") {
            wsBadge.classList.add("connecting");
        } else {
            wsBadge.classList.add("offline");
        }
        syncUiPermissions();
    }

    function updateSessionInfo() {
        if (!state.lobbyId) {
            sessionInfo.textContent = "Нет активной сессии";
            lobbyMeta.textContent = "Лобби не выбрано";
            renderPlayers();
            syncUiPermissions();
            return;
        }

        sessionInfo.textContent =
            `lobbyId=${state.lobbyId} | userId=${state.userId} | nickname=${state.nickname} | role=${isAdmin() ? "admin" : "player"}`;
        lobbyMeta.textContent =
            `Лобби: ${state.lobbyId} | статус: ${state.lobbyStatus || "unknown"} | игроков: ${state.players.length}`;
        syncUiPermissions();
    }

    function renderPlayers() {
        if (!state.players.length) {
            playersList.textContent = "Пока нет данных об участниках. Дождитесь события lobby-state.";
            syncLocalReadyState();
            syncUiPermissions();
            return;
        }
        playersList.innerHTML = state.players
            .map((player) => {
                const you = player.id === state.userId ? " (вы)" : "";
                const role = player.id === state.adminId ? "admin" : "player";
                const ready = player.ready ? "готов" : "не готов";
                return `<div class="player-item">${player.nickname}${you} - ${ready} [${role}]</div>`;
            })
            .join("");
        syncLocalReadyState();
        syncUiPermissions();
    }

    function showView(viewName) {
        Object.entries(views).forEach(([name, node]) => {
            node.classList.toggle("hidden", name !== viewName);
        });
    }

    async function renderRoute() {
        const route = parseRoute();

        if ((route.name === "home" || route.name === "list") && isInSession()) {
            const target = isGameStage() ? `game/${state.lobbyId}` : `lobby/${state.lobbyId}`;
            if (location.hash !== `#${target}`) {
                navigate(target);
            }
            return;
        }

        if (route.name === "game" && isInSession() && !isGameStage()) {
            const target = `lobby/${state.lobbyId}`;
            if (location.hash !== `#${target}`) {
                navigate(target);
            }
            return;
        }

        if ((route.name === "lobby" || route.name === "game") && route.lobbyId && !state.lobbyId) {
            state.lobbyId = route.lobbyId;
            updateSessionInfo();
        }

        showView(route.name);

        if (route.name === "list") {
            await refreshLobbyList();
        }

        syncUiPermissions();
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
        if (!state.lobbyId || !state.userId) {
            throw new Error("Сначала создайте или подключитесь к лобби");
        }
    }

    function applyAuth(auth) {
        state.lobbyId = auth.lobby.id;
        state.userId = auth.user.id;
        state.nickname = auth.user.nickname;
        state.adminId = auth.lobby.adminId;
        state.lobbyStatus = auth.lobby.status;
        dom.joinLobbyId.value = state.lobbyId;
        dom.homeLobbyId.value = state.lobbyId;
        updateSessionInfo();
        if (state.stompClient) {
            resubscribeTopics();
        }
    }

    function resetSession() {
        state.lobbyId = null;
        state.userId = null;
        state.nickname = null;
        state.adminId = null;
        state.lobbyStatus = null;
        state.players = [];
        state.isReadyLocal = false;
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
            state.adminId = payload.lobby.adminId || state.adminId;
            updateSessionInfo();
            return;
        }

        if (payload.startedBy && payload.lobby) {
            state.lobbyStatus = payload.lobby.status;
            state.adminId = payload.lobby.adminId || state.adminId;
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

    dom.goHomeBtn.addEventListener("click", () => navigate("home"));
    dom.goListBtn.addEventListener("click", () => navigate("list"));
    dom.goLobbyBtn.addEventListener("click", () => {
        if (!state.lobbyId) {
            logEvent("Сессия отсутствует, остаемся на главной");
            navigate("home");
            return;
        }
        navigate(`lobby/${state.lobbyId}`);
    });
    dom.goGameBtn.addEventListener("click", () => {
        if (!state.lobbyId) {
            logEvent("Сессия отсутствует, остаемся на главной");
            navigate("home");
            return;
        }
        navigate(`game/${state.lobbyId}`);
    });
    dom.homeOpenListBtn.addEventListener("click", () => navigate("list"));

    dom.createForm.addEventListener("submit", async (event) => {
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

    dom.homeJoinForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            await submitJoin(dom.homeLobbyId, dom.homeNickname);
        } catch (error) {
            logEvent(`Ошибка входа в лобби: ${error.message}`);
        }
    });

    dom.joinForm.addEventListener("submit", async (event) => {
        event.preventDefault();
        try {
            await submitJoin(dom.joinLobbyId, dom.joinName);
        } catch (error) {
            logEvent(`Ошибка входа в лобби: ${error.message}`);
        }
    });

    dom.refreshLobbiesBtn.addEventListener("click", refreshLobbyList);
    lobbiesList.addEventListener("click", (event) => {
        const button = event.target.closest(".join-suggest-btn");
        if (!button) {
            return;
        }
        const lobbyId = button.dataset.lobbyId;
        dom.joinLobbyId.value = lobbyId;
        dom.homeLobbyId.value = lobbyId;
        logEvent(`Подставлен код лобби: ${lobbyId}`);
    });

    dom.leaveLobbyBtn.addEventListener("click", async () => {
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

    dom.loadChatBtn.addEventListener("click", async () => {
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

    dom.chatForm.addEventListener("submit", (event) => {
        event.preventDefault();
        try {
            const message = dom.chatInput.value.trim();
            if (!message) {
                return;
            }
            logChat(`${state.nickname || "Вы"}: ${message} (локально, отправка...)`);
            sendStomp(`/app/lobby/${state.lobbyId}/chat`, { message });
            dom.chatInput.value = "";
        } catch (error) {
            logEvent(`Ошибка отправки чата: ${error.message}`);
        }
    });

    dom.readyToggleBtn.addEventListener("click", () => {
        try {
            const nextReadyState = !state.isReadyLocal;
            sendStomp(`/app/lobby/${state.lobbyId}/ready`, { ready: nextReadyState });
            state.isReadyLocal = nextReadyState;
            updateReadyToggleUi();
            logEvent(`Отправлено ready=${nextReadyState}`);
        } catch (error) {
            logEvent(`Ошибка смены ready: ${error.message}`);
        }
    });

    dom.statusOpenBtn.addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/status`, { status: "OPEN" });
            logEvent("Отправлена смена статуса OPEN");
        } catch (error) {
            logEvent(`Ошибка status OPEN: ${error.message}`);
        }
    });

    dom.statusCloseBtn.addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/status`, { status: "CLOSE" });
            logEvent("Отправлена смена статуса CLOSE");
        } catch (error) {
            logEvent(`Ошибка status CLOSE: ${error.message}`);
        }
    });

    dom.startGameBtn.addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/start`, {});
            logEvent("Отправлена команда start game");
        } catch (error) {
            logEvent(`Ошибка start game: ${error.message}`);
        }
    });

    dom.openCharacteristicForm.addEventListener("submit", (event) => {
        event.preventDefault();
        try {
            const characteristicName = dom.characteristicName.value.trim();
            if (!characteristicName) {
                return;
            }
            sendStomp(`/app/game/${state.lobbyId}/open-characteristic`, { characteristicName });
            logGame(`Отправлено открытие характеристики: ${characteristicName}`);
            dom.characteristicName.value = "";
        } catch (error) {
            logEvent(`Ошибка открытия характеристики: ${error.message}`);
        }
    });

    dom.shuffleBtn.addEventListener("click", () => {
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
    syncUiPermissions();
    logEvent("SPA frontend загружен");
})();

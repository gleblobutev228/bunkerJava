(function () {
    const state = {
        lobbyId: null,
        userId: null,
        nickname: null,
        stompClient: null
    };

    const sessionInfo = document.getElementById("session-info");
    const eventLog = document.getElementById("event-log");
    const chatLog = document.getElementById("chat-log");

    function logEvent(message, payload) {
        const line = `[${new Date().toLocaleTimeString()}] ${message}`;
        eventLog.textContent += payload ? `${line}\n${JSON.stringify(payload, null, 2)}\n\n` : `${line}\n`;
        eventLog.scrollTop = eventLog.scrollHeight;
    }

    function logChat(message, payload) {
        const line = `[${new Date().toLocaleTimeString()}] ${message}`;
        chatLog.textContent += payload ? `${line}\n${JSON.stringify(payload, null, 2)}\n\n` : `${line}\n`;
        chatLog.scrollTop = chatLog.scrollHeight;
    }

    function updateSessionInfo() {
        if (!state.lobbyId) {
            sessionInfo.textContent = "Нет активной сессии";
            return;
        }
        sessionInfo.textContent = `lobbyId=${state.lobbyId} | userId=${state.userId} | nickname=${state.nickname}`;
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
        } catch (e) {
            body = text;
        }

        if (!response.ok) {
            throw new Error(typeof body === "string" ? body : JSON.stringify(body));
        }
        return body;
    }

    function ensureSession() {
        if (!state.lobbyId) {
            throw new Error("Сначала создайте или подключитесь к лобби");
        }
    }

    function createStompClient() {
        return new StompJs.Client({
            brokerURL: `${location.protocol === "https:" ? "wss" : "ws"}://${location.host}/ws`,
            reconnectDelay: 2000,
            onConnect: () => {
                logEvent("WebSocket подключен");
                subscribeTopics();
            },
            onStompError: (frame) => {
                logEvent(`STOMP error: ${frame.headers.message || "unknown"}`, frame.body);
            },
            onWebSocketError: (event) => {
                logEvent("WebSocket error", String(event));
            }
        });
    }

    function subscribeTopics() {
        if (!state.stompClient || !state.stompClient.connected) {
            return;
        }
        const lobbyTopic = `/topic/lobby/${state.lobbyId}`;
        const gameTopic = `/topic/game/${state.lobbyId}`;

        state.stompClient.subscribe(lobbyTopic, (msg) => {
            const payload = JSON.parse(msg.body);
            logEvent(`Lobby event from ${lobbyTopic}`, payload);
            if (payload.message) {
                logChat("Chat message", payload);
            }
        });

        state.stompClient.subscribe(gameTopic, (msg) => {
            const payload = JSON.parse(msg.body);
            logEvent(`Game event from ${gameTopic}`, payload);
        });

        state.stompClient.subscribe("/user/queue/reply", (msg) => {
            const payload = JSON.parse(msg.body);
            logEvent("Private user event", payload);
        });
    }

    function connectWs() {
        ensureSession();
        if (state.stompClient && state.stompClient.connected) {
            logEvent("WebSocket уже подключен");
            return;
        }
        state.stompClient = createStompClient();
        state.stompClient.activate();
    }

    function disconnectWs() {
        if (state.stompClient) {
            state.stompClient.deactivate();
            state.stompClient = null;
        }
        logEvent("WebSocket отключен");
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

    async function loadChatHistory() {
        ensureSession();
        const history = await api(`/api/v1/lobbies/${state.lobbyId}/chat`, { method: "GET" });
        chatLog.textContent = "";
        history.forEach((entry) => logChat("History", entry));
        logEvent(`Загружено сообщений чата: ${history.length}`);
    }

    document.getElementById("create-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        try {
            const userName = document.getElementById("create-name").value.trim();
            const auth = await api("/api/v1/lobbies", {
                method: "POST",
                body: JSON.stringify({ userName })
            });
            state.lobbyId = auth.lobby.id;
            state.userId = auth.user.id;
            state.nickname = auth.user.nickname;
            updateSessionInfo();
            document.getElementById("join-lobby-id").value = state.lobbyId;
            logEvent("Лобби создано", auth);
        } catch (err) {
            logEvent(`Ошибка создания лобби: ${err.message}`);
        }
    });

    document.getElementById("join-form").addEventListener("submit", async (e) => {
        e.preventDefault();
        try {
            const lobbyId = document.getElementById("join-lobby-id").value.trim();
            const nickname = document.getElementById("join-name").value.trim();
            const auth = await api(`/api/v1/lobbies/${lobbyId}/join`, {
                method: "POST",
                body: JSON.stringify({ lobbyId, nickname })
            });
            state.lobbyId = auth.lobby.id;
            state.userId = auth.user.id;
            state.nickname = auth.user.nickname;
            updateSessionInfo();
            logEvent("Вход в лобби выполнен", auth);
        } catch (err) {
            logEvent(`Ошибка входа в лобби: ${err.message}`);
        }
    });

    document.getElementById("chat-form").addEventListener("submit", (e) => {
        e.preventDefault();
        try {
            const message = document.getElementById("chat-input").value.trim();
            sendStomp(`/app/lobby/${state.lobbyId}/chat`, { message });
            document.getElementById("chat-input").value = "";
            logEvent("Chat message отправлен");
        } catch (err) {
            logEvent(`Ошибка отправки чата: ${err.message}`);
        }
    });

    document.getElementById("open-characteristic-form").addEventListener("submit", (e) => {
        e.preventDefault();
        try {
            const characteristicName = document.getElementById("characteristic-name").value.trim();
            sendStomp(`/app/game/${state.lobbyId}/open-characteristic`, { characteristicName });
            logEvent(`Открытие характеристики отправлено: ${characteristicName}`);
        } catch (err) {
            logEvent(`Ошибка открытия характеристики: ${err.message}`);
        }
    });

    document.getElementById("ready-on-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/ready`, { ready: true });
        } catch (err) {
            logEvent(`Ошибка ready ON: ${err.message}`);
        }
    });
    document.getElementById("ready-off-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/ready`, { ready: false });
        } catch (err) {
            logEvent(`Ошибка ready OFF: ${err.message}`);
        }
    });
    document.getElementById("start-game-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/start`, {});
        } catch (err) {
            logEvent(`Ошибка start game: ${err.message}`);
        }
    });
    document.getElementById("shuffle-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/game/${state.lobbyId}/shuffle-cards`, {});
        } catch (err) {
            logEvent(`Ошибка shuffle: ${err.message}`);
        }
    });
    document.getElementById("status-open-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/status`, { status: "OPEN" });
        } catch (err) {
            logEvent(`Ошибка status OPEN: ${err.message}`);
        }
    });
    document.getElementById("status-close-btn").addEventListener("click", () => {
        try {
            sendStomp(`/app/lobby/${state.lobbyId}/status`, { status: "CLOSE" });
        } catch (err) {
            logEvent(`Ошибка status CLOSE: ${err.message}`);
        }
    });

    document.getElementById("connect-ws-btn").addEventListener("click", () => {
        try {
            connectWs();
        } catch (err) {
            logEvent(`Ошибка подключения WS: ${err.message}`);
        }
    });
    document.getElementById("disconnect-ws-btn").addEventListener("click", disconnectWs);
    document.getElementById("load-chat-btn").addEventListener("click", async () => {
        try {
            await loadChatHistory();
        } catch (err) {
            logEvent(`Ошибка загрузки истории: ${err.message}`);
        }
    });

    logEvent("MVP frontend загружен");
})();

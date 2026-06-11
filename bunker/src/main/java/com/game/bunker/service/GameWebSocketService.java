package com.game.bunker.service;

import com.game.bunker.dto.ws.AdminGameActionMessage;
import com.game.bunker.dto.ws.GameActionMessage;
import com.game.bunker.dto.ws.GameStartedMessage;
import com.game.bunker.dto.ws.LobbyChatMessage;
import com.game.bunker.dto.ws.LobbyChatRequest;
import com.game.bunker.dto.ws.LobbyReadyRequest;
import com.game.bunker.dto.ws.LobbyStateMessage;
import com.game.bunker.dto.ws.LobbyStatusMessage;
import com.game.bunker.dto.ws.LobbyStatusRequest;
import com.game.bunker.dto.ws.OpenCharacteristicRequest;
import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import java.security.Principal;
import java.time.Instant;

/**
 * Сервис бизнес-логики STOMP-команд игры.
 * Выполняет действия игроков и администратора, очищает чат от XSS и публикует события
 * в публичные топики лобби/игры через Spring Messaging.
 */
@Service
public class GameWebSocketService {
    private static final int MAX_CHAT_MESSAGE_LENGTH = 500;

    private final RedisPubSubService redisPubSubService;
    private final LobbyService lobbyService;
    private final UserService userService;

    public GameWebSocketService(RedisPubSubService redisPubSubService,
                                LobbyService lobbyService,
                                UserService userService) {
        this.redisPubSubService = redisPubSubService;
        this.lobbyService = lobbyService;
        this.userService = userService;
    }

    /**
     * Отправляет сообщение чата всем участникам лобби.
     *
     * @param lobbyId код лобби.
     * @param request входящее сообщение.
     * @param principal текущий STOMP principal.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketController /app/lobby/{lobbyId}/chat.
     * - Куда (Outbound): UserService, SimpMessagingTemplate, /topic/lobby/{lobbyId}.
     */
    // TODO(senior): Запись истории чата и публикация события разделены; при сбое publish сообщение сохранится, но клиенты его не увидят.
    public void sendChatMessage(String lobbyId, LobbyChatRequest request, Principal principal) {
        User sender = currentUser(principal);
        LobbyChatMessage message = new LobbyChatMessage(
                lobbyId,
                sender.getId(),
                sender.getNickname(),
                sanitizeChatMessage(request.message()),
                Instant.now()
        );
        
        // Сохраняем сообщение в историю чата в Redis
        lobbyService.addChatMessage(lobbyId, message);

        // STOMP publish отправляет событие всем подписчикам лобби через Redis.
        redisPubSubService.publishChatBestEffort(
                "/topic/lobby/" + lobbyId,
                RedisPubSubService.RedisWsEventType.LOBBY_CHAT,
                message
        );
    }

    /**
     * Обновляет ready-флаг текущего игрока и публикует состав лобби.
     *
     * @param lobbyId код лобби.
     * @param request новое значение готовности.
     * @param principal текущий STOMP principal.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketController /app/lobby/{lobbyId}/ready.
     * - Куда (Outbound): UserService, Redis user hash, /topic/lobby/{lobbyId}.
     */
    // TODO(senior): setReady и publishLobbyState не атомарны; при ошибке рассылки состояние изменится без уведомления клиентов.
    public void updateReadyState(String lobbyId, LobbyReadyRequest request, Principal principal) {
        User sender = currentUser(principal);
        userService.setReady(sender.getId(), request.ready());
        publishLobbyState(lobbyId);
    }

    /**
     * Изменяет статус лобби по команде администратора.
     *
     * @param lobbyId код лобби.
     * @param request целевой статус.
     * @param principal текущий STOMP principal администратора.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketController /app/lobby/{lobbyId}/status.
     * - Куда (Outbound): LobbyService, Redis lobby hash, /topic/lobby/{lobbyId}.
     */
    // TODO(senior): Изменение статуса и публикация события не оформлены как outbox/transactional messaging; возможна рассинхронизация UI.
    public void updateLobbyStatus(String lobbyId, LobbyStatusRequest request, Principal principal) {
        String adminId = requireUserId(principal);
        Lobby lobby = lobbyService.updateStatus(lobbyId, parseLobbyStatus(request.status()), adminId);
        // STOMP publish сообщает всем участникам о смене статуса лобби через Redis.
        redisPubSubService.publishCriticalBroadcastWithRetry(
                "/topic/lobby/" + lobbyId,
                RedisPubSubService.RedisWsEventType.LOBBY_STATUS_CHANGED,
                new LobbyStatusMessage(lobbyId, adminId, lobby)
        );
    }

    /**
     * Запускает игру и публикует событие старта в игровой топик.
     *
     * @param lobbyId код лобби.
     * @param principal текущий STOMP principal администратора.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketController /app/lobby/{lobbyId}/start.
     * - Куда (Outbound): LobbyService, Redis status/TTL, /topic/game/{lobbyId}.
     */
    // TODO(senior): После startGame состояние уже GAME, но publish может упасть; нужен outbox/retry worker или идемпотентный recovery для клиентов.
    public void startGame(String lobbyId, Principal principal) {
        String adminId = requireUserId(principal);
        Lobby lobby = lobbyService.startGame(lobbyId, adminId);
        // Игровые события идут в отдельный topic, чтобы отделить их от фазы лобби (через Redis).
        redisPubSubService.publishCriticalBroadcastWithRetry(
                "/topic/game/" + lobbyId,
                RedisPubSubService.RedisWsEventType.GAME_STARTED,
                new GameStartedMessage(lobbyId, adminId, lobby)
        );
    }

    /**
     * Открывает характеристику текущего игрока и публикует обновленный публичный вид игрока.
     *
     * @param lobbyId код лобби.
     * @param request имя характеристики.
     * @param principal текущий STOMP principal игрока.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketController /app/game/{lobbyId}/open-characteristic.
     * - Куда (Outbound): UserService, Redis user hash, /topic/game/{lobbyId}.
     */
    // TODO(senior): Открытие характеристики не проверяет принадлежность actor к lobbyId внутри домена; сейчас это доверено внешнему @PreAuthorize.
    public void openCharacteristic(String lobbyId, OpenCharacteristicRequest request, Principal principal) {
        User actor = currentUser(principal);
        userService.openCharacteristic(actor.getId(), request.characteristicName());
        // STOMP publish сообщает всем игрокам о новом видимом состоянии персонажа через Redis.
        redisPubSubService.publishCriticalBroadcastWithRetry(
                "/topic/game/" + lobbyId,
                RedisPubSubService.RedisWsEventType.GAME_ACTION,
                new GameActionMessage(
                        lobbyId,
                        actor.getId(),
                        "OPEN_CHARACTERISTIC",
                        userService.getVisibleUser(actor.getId())
                )
        );
    }

    /**
     * Выполняет админское игровое действие перемешивания карт.
     *
     * @param lobbyId код лобби.
     * @param principal текущий STOMP principal администратора.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketController /app/game/{lobbyId}/shuffle-cards.
     * - Куда (Outbound): LobbyService, Redis lobby hash, /topic/game/{lobbyId}.
     */
    // TODO(senior): Доменное действие и событие разнесены; при ошибке publish версия shuffle изменится, а клиенты не получат команду.
    public void shuffleCards(String lobbyId, Principal principal) {
        String adminId = requireUserId(principal);
        long version = lobbyService.shuffleCards(lobbyId, adminId);
        // Версия действия хранится в Redis и отправляется через STOMP для синхронизации клиентов.
        redisPubSubService.publishCriticalBroadcastWithRetry(
                "/topic/game/" + lobbyId,
                RedisPubSubService.RedisWsEventType.ADMIN_GAME_ACTION,
                new AdminGameActionMessage(lobbyId, adminId, "SHUFFLE_CARDS", version)
        );
    }

    private User currentUser(Principal principal) {
        return userService.getUser(requireUserId(principal));
    }

    // TODO(senior): Сервис бизнес-логики зависит от java.security.Principal; лучше передавать userId из контроллера, чтобы не смешивать transport и domain.
    private String requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("WebSocket user is not authenticated");
        }
        return principal.getName();
    }

    // TODO(senior): valueOf на внешнем вводе бросит IllegalArgumentException без нормального error contract; вынести в валидируемый enum DTO.
    private LobbyStatus parseLobbyStatus(String rawStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            throw new IllegalArgumentException("Lobby status is required");
        }

        LobbyStatus status = LobbyStatus.valueOf(rawStatus.trim().toUpperCase());
        if (status != LobbyStatus.OPEN && status != LobbyStatus.CLOSE) {
            throw new IllegalArgumentException("Lobby status must be OPEN or CLOSE");
        }
        return status;
    }

    private void publishLobbyState(String lobbyId) {
        // STOMP publish рассылает актуальный состав игроков всем подписчикам лобби.
        redisPubSubService.publishCriticalBroadcastWithRetry(
                "/topic/lobby/" + lobbyId,
                RedisPubSubService.RedisWsEventType.LOBBY_STATE,
                new LobbyStateMessage(lobbyId, lobbyService.getVisibleLobbyUsers(lobbyId))
        );
    }

    private String sanitizeChatMessage(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("Chat message is empty");
        }

        String trimmed = rawMessage.trim();
        if (trimmed.length() > MAX_CHAT_MESSAGE_LENGTH) {
            trimmed = trimmed.substring(0, MAX_CHAT_MESSAGE_LENGTH);
        }
        return HtmlUtils.htmlEscape(trimmed);
    }
}

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
import org.springframework.messaging.simp.SimpMessagingTemplate;
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

    private final SimpMessagingTemplate messagingTemplate;
    private final LobbyService lobbyService;
    private final UserService userService;

    public GameWebSocketService(SimpMessagingTemplate messagingTemplate,
                                LobbyService lobbyService,
                                UserService userService) {
        this.messagingTemplate = messagingTemplate;
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
    public void sendChatMessage(String lobbyId, LobbyChatRequest request, Principal principal) {
        User sender = currentUser(principal);
        // STOMP publish отправляет событие всем подписчикам лобби.
        messagingTemplate.convertAndSend(
                "/topic/lobby/" + lobbyId,
                new LobbyChatMessage(
                        lobbyId,
                        sender.getId(),
                        sender.getNickname(),
                        sanitizeChatMessage(request.message()),
                        Instant.now()
                )
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
    public void updateLobbyStatus(String lobbyId, LobbyStatusRequest request, Principal principal) {
        String adminId = requireUserId(principal);
        Lobby lobby = lobbyService.updateStatus(lobbyId, parseLobbyStatus(request.status()), adminId);
        // STOMP publish сообщает всем участникам о смене статуса лобби.
        messagingTemplate.convertAndSend(
                "/topic/lobby/" + lobbyId,
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
    public void startGame(String lobbyId, Principal principal) {
        String adminId = requireUserId(principal);
        Lobby lobby = lobbyService.startGame(lobbyId, adminId);
        // Игровые события идут в отдельный topic, чтобы отделить их от фазы лобби.
        messagingTemplate.convertAndSend(
                "/topic/game/" + lobbyId,
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
    public void openCharacteristic(String lobbyId, OpenCharacteristicRequest request, Principal principal) {
        User actor = currentUser(principal);
        userService.openCharacteristic(actor.getId(), request.characteristicName());
        // STOMP publish сообщает всем игрокам о новом видимом состоянии персонажа.
        messagingTemplate.convertAndSend(
                "/topic/game/" + lobbyId,
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
    public void shuffleCards(String lobbyId, Principal principal) {
        String adminId = requireUserId(principal);
        long version = lobbyService.shuffleCards(lobbyId, adminId);
        // Версия действия хранится в Redis и отправляется через STOMP для синхронизации клиентов.
        messagingTemplate.convertAndSend(
                "/topic/game/" + lobbyId,
                new AdminGameActionMessage(lobbyId, adminId, "SHUFFLE_CARDS", version)
        );
    }

    private User currentUser(Principal principal) {
        return userService.getUser(requireUserId(principal));
    }

    private String requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("WebSocket user is not authenticated");
        }
        return principal.getName();
    }

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
        messagingTemplate.convertAndSend(
                "/topic/lobby/" + lobbyId,
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

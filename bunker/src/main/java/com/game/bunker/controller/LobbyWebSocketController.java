package com.game.bunker.controller;

import com.game.bunker.dto.ws.LobbyChatMessage;
import com.game.bunker.dto.ws.LobbyChatRequest;
import com.game.bunker.dto.ws.LobbyReadyRequest;
import com.game.bunker.dto.ws.LobbyStateMessage;
import com.game.bunker.entity.User;
import com.game.bunker.service.LobbyService;
import com.game.bunker.service.UserService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

import java.security.Principal;
import java.time.Instant;

@Controller
public class LobbyWebSocketController {
    private static final int MAX_CHAT_MESSAGE_LENGTH = 500;

    private final SimpMessagingTemplate messagingTemplate;
    private final LobbyService lobbyService;
    private final UserService userService;

    public LobbyWebSocketController(SimpMessagingTemplate messagingTemplate,
                                    LobbyService lobbyService,
                                    UserService userService) {
        this.messagingTemplate = messagingTemplate;
        this.lobbyService = lobbyService;
        this.userService = userService;
    }

    @MessageMapping("/lobby/{lobbyId}/chat")
    public void sendChatMessage(@DestinationVariable String lobbyId,
                                LobbyChatRequest request,
                                Principal principal) {
        User sender = requireLobbyMember(lobbyId, principal);
        LobbyChatMessage message = new LobbyChatMessage(
                lobbyId,
                sender.getId(),
                sender.getNickname(),
                sanitizeChatMessage(request.message()),
                Instant.now()
        );

        messagingTemplate.convertAndSend("/topic/lobby/" + lobbyId, message);
    }

    @MessageMapping("/lobby/{lobbyId}/ready")
    public void updateReadyState(@DestinationVariable String lobbyId,
                                 LobbyReadyRequest request,
                                 Principal principal) {
        User sender = requireLobbyMember(lobbyId, principal);
        userService.setReady(sender.getId(), request.ready());

        messagingTemplate.convertAndSend(
                "/topic/lobby/" + lobbyId,
                new LobbyStateMessage(lobbyId, lobbyService.getVisibleLobbyUsers(lobbyId))
        );
    }

    private User requireLobbyMember(String lobbyId, Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("WebSocket user is not authenticated");
        }

        User user = userService.getUser(principal.getName());
        if (!lobbyId.equals(user.getLobbyId())) {
            throw new IllegalArgumentException("User does not belong to lobby: " + lobbyId);
        }
        return user;
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

package com.game.bunker.controller;

import com.game.bunker.dto.StartGameResponse;
import com.game.bunker.dto.ws.GameActionMessage;
import com.game.bunker.dto.ws.GameStartedMessage;
import com.game.bunker.dto.ws.OpenCharacteristicRequest;
import com.game.bunker.dto.ws.PersonalGameDataMessage;
import com.game.bunker.entity.User;
import com.game.bunker.security.JwtProvider;
import com.game.bunker.service.AuthService;
import com.game.bunker.service.UserService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class GameWebSocketController {
    private final SimpMessagingTemplate messagingTemplate;
    private final AuthService authService;
    private final UserService userService;
    private final JwtProvider jwtProvider;

    public GameWebSocketController(SimpMessagingTemplate messagingTemplate,
                                   AuthService authService,
                                   UserService userService,
                                   JwtProvider jwtProvider) {
        this.messagingTemplate = messagingTemplate;
        this.authService = authService;
        this.userService = userService;
        this.jwtProvider = jwtProvider;
    }

    @MessageMapping("/lobby/{lobbyId}/start")
    public void startGame(@DestinationVariable String lobbyId, Principal principal) {
        String userId = requireUserId(principal);
        StartGameResponse response = authService.startGame(lobbyId, userId);

        messagingTemplate.convertAndSend(
                "/topic/game/" + lobbyId,
                new GameStartedMessage(lobbyId, userId, response.lobby())
        );

        for (String playerId : response.lobby().getUserIds()) {
            messagingTemplate.convertAndSendToUser(
                    playerId,
                    "/queue/reply",
                    new PersonalGameDataMessage(jwtProvider.generateToken(playerId), userService.getUser(playerId))
            );
        }
    }

    @MessageMapping("/game/{lobbyId}/action")
    public void openCharacteristic(@DestinationVariable String lobbyId,
                                   OpenCharacteristicRequest request,
                                   Principal principal) {
        User actor = requireLobbyMember(lobbyId, principal);
        userService.openCharacteristic(actor.getId(), request.characteristicName());

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

    private User requireLobbyMember(String lobbyId, Principal principal) {
        User user = userService.getUser(requireUserId(principal));
        if (!lobbyId.equals(user.getLobbyId())) {
            throw new IllegalArgumentException("User does not belong to lobby: " + lobbyId);
        }
        return user;
    }

    private String requireUserId(Principal principal) {
        if (principal == null || principal.getName() == null || principal.getName().isBlank()) {
            throw new IllegalArgumentException("WebSocket user is not authenticated");
        }
        return principal.getName();
    }
}

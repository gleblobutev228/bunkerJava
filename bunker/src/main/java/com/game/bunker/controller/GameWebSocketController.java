package com.game.bunker.controller;

import com.game.bunker.dto.ws.LobbyChatRequest;
import com.game.bunker.dto.ws.LobbyReadyRequest;
import com.game.bunker.dto.ws.LobbyStatusRequest;
import com.game.bunker.dto.ws.OpenCharacteristicRequest;
import com.game.bunker.service.GameWebSocketService;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
public class GameWebSocketController {
    private final GameWebSocketService gameWebSocketService;

    public GameWebSocketController(GameWebSocketService gameWebSocketService) {
        this.gameWebSocketService = gameWebSocketService;
    }

    @MessageMapping("/lobby/{lobbyId}/chat")
    @PreAuthorize("@lobbySecurity.isMember(#lobbyId, authentication.name) and @lobbySecurity.isLobbyStage(#lobbyId)")
    public void sendChatMessage(@DestinationVariable String lobbyId,
                                LobbyChatRequest request,
                                Principal principal) {
        gameWebSocketService.sendChatMessage(lobbyId, request, principal);
    }

    @MessageMapping("/lobby/{lobbyId}/ready")
    @PreAuthorize("@lobbySecurity.isMember(#lobbyId, authentication.name) and @lobbySecurity.isLobbyStage(#lobbyId)")
    public void updateReadyState(@DestinationVariable String lobbyId,
                                 LobbyReadyRequest request,
                                 Principal principal) {
        gameWebSocketService.updateReadyState(lobbyId, request, principal);
    }

    @MessageMapping("/lobby/{lobbyId}/status")
    @PreAuthorize("@lobbySecurity.isAdmin(#lobbyId, authentication.name) and @lobbySecurity.isLobbyStage(#lobbyId)")
    public void updateLobbyStatus(@DestinationVariable String lobbyId,
                                  LobbyStatusRequest request,
                                  Principal principal) {
        gameWebSocketService.updateLobbyStatus(lobbyId, request, principal);
    }

    @MessageMapping("/lobby/{lobbyId}/start")
    @PreAuthorize("@lobbySecurity.isAdmin(#lobbyId, authentication.name) and @lobbySecurity.isLobbyStage(#lobbyId)")
    public void startGame(@DestinationVariable String lobbyId, Principal principal) {
        gameWebSocketService.startGame(lobbyId, principal);
    }

    @MessageMapping("/game/{lobbyId}/open-characteristic")
    @PreAuthorize("@lobbySecurity.isMember(#lobbyId, authentication.name) and @lobbySecurity.isGameStage(#lobbyId)")
    public void openCharacteristic(@DestinationVariable String lobbyId,
                                   OpenCharacteristicRequest request,
                                   Principal principal) {
        gameWebSocketService.openCharacteristic(lobbyId, request, principal);
    }

    @MessageMapping("/game/{lobbyId}/shuffle-cards")
    @PreAuthorize("@lobbySecurity.isAdmin(#lobbyId, authentication.name) and @lobbySecurity.isGameStage(#lobbyId)")
    public void shuffleCards(@DestinationVariable String lobbyId, Principal principal) {
        gameWebSocketService.shuffleCards(lobbyId, principal);
    }
}

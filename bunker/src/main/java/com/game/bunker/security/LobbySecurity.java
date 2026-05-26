package com.game.bunker.security;

import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.service.LobbyService;
import org.springframework.stereotype.Component;

@Component("lobbySecurity")
public class LobbySecurity {
    private final LobbyService lobbyService;

    public LobbySecurity(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    public boolean isMember(String lobbyId, String userId) {
        return userId != null && lobbyService.isMember(lobbyId, userId);
    }

    public boolean isAdmin(String lobbyId, String userId) {
        return userId != null && lobbyService.isAdmin(lobbyId, userId);
    }

    public boolean isLobbyStage(String lobbyId) {
        Lobby lobby = lobbyService.getLobby(lobbyId);
        return lobby.getStatus() == LobbyStatus.OPEN || lobby.getStatus() == LobbyStatus.CLOSE;
    }

    public boolean isGameStage(String lobbyId) {
        return lobbyService.getLobby(lobbyId).getStatus() == LobbyStatus.GAME_STARTED;
    }
}

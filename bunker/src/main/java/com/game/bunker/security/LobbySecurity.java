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

    // TODO(senior): В связке с isMember/isAdmin в @PreAuthorize один запрос может читать лобби несколько раз; объединить проверки или кэшировать контекст.
    public boolean isLobbyStage(String lobbyId) {
        Lobby lobby = lobbyService.getLobby(lobbyId);
        return lobby.getStatus() == LobbyStatus.OPEN || lobby.getStatus() == LobbyStatus.CLOSE;
    }

    // TODO(senior): Проверка стадии каждый раз ходит в Redis через LobbyService; на горячих WebSocket-командах нужен единый authorization snapshot.
    public boolean isGameStage(String lobbyId) {
        return lobbyService.getLobby(lobbyId).getStatus() == LobbyStatus.GAME;
    }
}

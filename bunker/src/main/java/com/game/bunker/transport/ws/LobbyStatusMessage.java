package com.game.bunker.transport.ws;

import com.game.bunker.lobby.entity.Lobby;

public record LobbyStatusMessage(
        String lobbyId,
        String changedBy,
        Lobby lobby
) {
}

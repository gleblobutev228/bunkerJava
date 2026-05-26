package com.game.bunker.dto.ws;

import com.game.bunker.entity.Lobby;

public record LobbyStatusMessage(
        String lobbyId,
        String changedBy,
        Lobby lobby
) {
}

package com.game.bunker.dto.ws;

import com.game.bunker.entity.Lobby;

public record GameStartedMessage(
        String lobbyId,
        String startedBy,
        Lobby lobby
) {
}

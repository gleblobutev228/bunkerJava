package com.game.bunker.transport.ws;

import com.game.bunker.lobby.entity.Lobby;

public record GameStartedMessage(
        String lobbyId,
        String startedBy,
        Lobby lobby
) {
}

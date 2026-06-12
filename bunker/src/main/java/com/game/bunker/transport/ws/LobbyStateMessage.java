package com.game.bunker.transport.ws;

import java.util.List;

public record LobbyStateMessage(
        String lobbyId,
        List<PlayerPublicView> players
) {
}

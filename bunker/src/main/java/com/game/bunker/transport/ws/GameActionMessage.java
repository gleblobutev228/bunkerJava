package com.game.bunker.transport.ws;

public record GameActionMessage(
        String lobbyId,
        String actorId,
        String action,
        PlayerPublicView player
) {
}

package com.game.bunker.transport.ws;

public record ClientSessionCommandMessage(
        String command,
        String lobbyId,
        String reason
) {
}

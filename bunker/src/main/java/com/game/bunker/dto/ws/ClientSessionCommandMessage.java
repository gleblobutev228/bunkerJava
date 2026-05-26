package com.game.bunker.dto.ws;

public record ClientSessionCommandMessage(
        String command,
        String lobbyId,
        String reason
) {
}

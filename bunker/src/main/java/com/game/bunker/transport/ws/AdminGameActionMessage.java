package com.game.bunker.transport.ws;

public record AdminGameActionMessage(
        String lobbyId,
        String adminId,
        String action,
        long version
) {
}

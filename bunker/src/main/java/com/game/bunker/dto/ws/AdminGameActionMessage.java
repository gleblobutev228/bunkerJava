package com.game.bunker.dto.ws;

public record AdminGameActionMessage(
        String lobbyId,
        String adminId,
        String action,
        long version
) {
}

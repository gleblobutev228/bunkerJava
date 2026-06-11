package com.game.bunker.transport.ws;

import java.time.Instant;

public record LobbyChatMessage(
        String lobbyId,
        String senderId,
        String senderNickname,
        String message,
        Instant sentAt
) {
}

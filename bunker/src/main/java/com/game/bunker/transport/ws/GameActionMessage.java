package com.game.bunker.transport.ws;

import com.game.bunker.user.entity.User;

public record GameActionMessage(
        String lobbyId,
        String actorId,
        String action,
        User user
) {
}

package com.game.bunker.dto.ws;

import com.game.bunker.entity.User;

public record GameActionMessage(
        String lobbyId,
        String actorId,
        String action,
        User user
) {
}

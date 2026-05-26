package com.game.bunker.dto.ws;

import com.game.bunker.entity.User;

public record PersonalGameDataMessage(
        String token,
        User user
) {
}

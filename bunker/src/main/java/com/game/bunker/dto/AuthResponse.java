package com.game.bunker.dto;

import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.User;

public record AuthResponse(
        String token,
        User user,
        Lobby lobby
) {
}

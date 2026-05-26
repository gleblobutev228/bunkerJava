package com.game.bunker.dto;

import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.User;

public record AuthResponse(
        User user,
        Lobby lobby
) {
}

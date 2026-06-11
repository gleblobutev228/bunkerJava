package com.game.bunker.user.dto;

import com.game.bunker.lobby.entity.Lobby;
import com.game.bunker.user.entity.User;

public record AuthResponse(
        User user,
        Lobby lobby
) {
}

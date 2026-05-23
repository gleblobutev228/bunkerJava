package com.game.bunker.dto;

import com.game.bunker.entity.Lobby;

public record StartGameResponse(
        String token,
        Lobby lobby
) {
}

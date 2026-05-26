package com.game.bunker.dto.ws;

import com.game.bunker.entity.User;

import java.util.List;

public record LobbyStateMessage(
        String lobbyId,
        List<User> players
) {
}

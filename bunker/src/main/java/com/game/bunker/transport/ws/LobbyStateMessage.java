package com.game.bunker.transport.ws;

import com.game.bunker.user.entity.User;

import java.util.List;

public record LobbyStateMessage(
        String lobbyId,
        List<User> players
) {
}

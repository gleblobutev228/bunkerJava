package com.game.bunker.lobby.dto;

import com.game.bunker.lobby.entity.LobbyStatus;

public record LobbyListItemResponse(
        String id,
        LobbyStatus status,
        String adminId,
        int playersCount
) {
}

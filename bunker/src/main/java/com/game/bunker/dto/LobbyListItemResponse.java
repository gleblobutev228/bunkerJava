package com.game.bunker.dto;

import com.game.bunker.entity.LobbyStatus;

public record LobbyListItemResponse(
        String id,
        LobbyStatus status,
        String adminId,
        int playersCount
) {
}

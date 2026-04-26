package com.game.bunker.dto.response;

import com.game.bunker.dto.UserSummaryDto;
import com.game.bunker.entity.LobbyStatus;

import java.util.Set;

public record LobbyCreationResponseDto(
        String code,
        String status,
        Long adminId,
        Set<UserSummaryDto> users)
{}
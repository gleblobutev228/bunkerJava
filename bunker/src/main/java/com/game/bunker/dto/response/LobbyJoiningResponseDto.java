package com.game.bunker.dto.response;

import com.game.bunker.dto.UserSummaryDto;

import java.util.Set;

public record LobbyJoiningResponseDto(
        String code,
        String status,
        Long adminId,
        Set<UserSummaryDto> users)
{}
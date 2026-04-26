package com.game.bunker.dto;

import java.util.Set;

public record UserSummaryDto(
        Long id,
        String userName,
        Set<String> roles
) {}

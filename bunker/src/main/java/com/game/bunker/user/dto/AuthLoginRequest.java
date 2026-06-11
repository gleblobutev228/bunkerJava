package com.game.bunker.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthLoginRequest(
        @NotBlank(message = "Имя не может быть пустым")
        @Size(min = 1, max = 20, message = "от 1 до 20 символов")
        String nickname,
        String lobbyId
) {
}

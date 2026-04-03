package com.game.bunker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserCreationRequest(
        @NotBlank(message = "Имя не может быть пустым")
        @Size(min = 1, max = 20, message = "от 2 до 20 символов")
        String userName
) {
}

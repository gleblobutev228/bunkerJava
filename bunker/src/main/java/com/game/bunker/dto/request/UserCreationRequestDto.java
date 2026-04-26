package com.game.bunker.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserCreationRequestDto {

    @NotBlank(message = "Имя не может быть пустым")
    @Size(min = 1, max = 20, message = "от 2 до 20 символов")
    private String userName;

}

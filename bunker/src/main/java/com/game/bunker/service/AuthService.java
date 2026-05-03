package com.game.bunker.service;

import com.game.bunker.dto.request.UserCreationRequestDto;
import com.game.bunker.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtService jwtService;
    private final UserService userService;

    public ResponseCookie createAuthCookie(UserCreationRequestDto dto) {
        User user = userService.getUserByNickname(dto.getUserName())
                .orElseGet(() -> userService.createUser(dto));
        user = userService.saveUser(user);
        String token = jwtService.generateToken(user);

           return ResponseCookie.from("jwt_token", token)
                .httpOnly(true)
                .secure(false)
                .path("/")
                .maxAge(Duration.ofDays(1))
                .sameSite("Lax")
                .build();
    }
}
package com.game.bunker.controller;

import com.game.bunker.dto.AuthLoginRequest;
import com.game.bunker.dto.AuthResponse;
import com.game.bunker.dto.StartGameResponse;
import com.game.bunker.security.JwtFilter;
import com.game.bunker.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/api/v1")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthLoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/auth/reconnect")
    public ResponseEntity<AuthResponse> reconnect(HttpServletRequest request) {
        String userId = currentUserId(request);
        String token = extractBearerToken(request);
        return ResponseEntity.ok(authService.reconnect(userId, token));
    }

    @PostMapping("/lobbies/{lobbyId}/start")
    public ResponseEntity<StartGameResponse> startGame(@PathVariable String lobbyId, HttpServletRequest request) {
        String userId = currentUserId(request);
        return ResponseEntity.ok(authService.startGame(lobbyId, userId));
    }

    private String currentUserId(HttpServletRequest request) {
        Object userId = request.getAttribute(JwtFilter.USER_ID_ATTRIBUTE);
        if (!(userId instanceof String value) || value.isBlank()) {
            throw new UnauthorizedException("JWT token is missing or invalid");
        }
        return value;
    }

    private String extractBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new UnauthorizedException("JWT token is missing or invalid");
        }
        return authorizationHeader.substring("Bearer ".length());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<String> handleUnauthorized(UnauthorizedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(exception.getMessage());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> handleNotFound(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    private static class UnauthorizedException extends RuntimeException {
        private UnauthorizedException(String message) {
            super(message);
        }
    }
}

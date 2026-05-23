package com.game.bunker.service;

import com.game.bunker.dto.AuthLoginRequest;
import com.game.bunker.dto.AuthResponse;
import com.game.bunker.dto.StartGameResponse;
import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.User;
import com.game.bunker.security.JwtProvider;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final LobbyService lobbyService;
    private final UserService userService;
    private final JwtProvider jwtProvider;

    public AuthService(LobbyService lobbyService, UserService userService, JwtProvider jwtProvider) {
        this.lobbyService = lobbyService;
        this.userService = userService;
        this.jwtProvider = jwtProvider;
    }

    public AuthResponse login(AuthLoginRequest request) {
        Lobby lobby = resolveLobby(request.lobbyId());
        User user = lobbyService.addUser(lobby.getId(), request.nickname());
        String token = jwtProvider.generateToken(user.getId());
        return new AuthResponse(token, user, lobbyService.getLobby(lobby.getId()));
    }

    public AuthResponse reconnect(String userId, String token) {
        User user = userService.getUser(userId);
        Lobby lobby = lobbyService.getLobby(user.getLobbyId());
        return new AuthResponse(token, user, lobby);
    }

    public StartGameResponse startGame(String lobbyId, String userId) {
        User user = userService.getUser(userId);
        if (!lobbyId.equals(user.getLobbyId())) {
            throw new IllegalArgumentException("User does not belong to lobby: " + lobbyId);
        }

        Lobby lobby = lobbyService.startGame(lobbyId);
        String refreshedToken = jwtProvider.generateToken(userId);
        return new StartGameResponse(refreshedToken, lobby);
    }

    private Lobby resolveLobby(String lobbyId) {
        if (lobbyId == null || lobbyId.isBlank()) {
            return lobbyService.createLobby();
        }
        return lobbyService.getLobby(lobbyId);
    }
}

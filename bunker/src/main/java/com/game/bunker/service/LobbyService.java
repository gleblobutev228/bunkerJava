package com.game.bunker.service;

import com.game.bunker.dto.UserCreationRequest;
import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.entity.User;
import com.game.bunker.repository.LobbyRepository;
import com.game.bunker.repository.UserRepository;
import com.game.bunker.utils.generator.LobbyCodeGenerator;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class LobbyService {
    private final LobbyRepository lobbyRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public LobbyService(LobbyRepository lobbyRepository, UserRepository userRepository, UserService userService) {
        this.lobbyRepository = lobbyRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    public Lobby createLobby() {
        String lobbyId = generateUniqueLobbyId();
        Lobby lobby = new Lobby();
        lobby.setId(lobbyId);
        lobby.setStatus(LobbyStatus.OPEN);
        return lobbyRepository.saveWithInitialTtl(lobby);
    }

    public Lobby createLobby(UserCreationRequest adminRequest) {
        Lobby lobby = createLobby();
        addUser(lobby.getId(), adminRequest.userName());
        return lobbyRepository.findById(lobby.getId()).orElse(lobby);
    }

    public User addUser(String lobbyId, String nickname) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
        Duration ttl = lobbyRepository.getRemainingTtl(lobbyId).orElse(LobbyRepository.SESSION_TTL);
        User user = userService.generateAndSaveUser(lobbyId, nickname, ttl);
        lobbyRepository.addUser(lobby.getId(), user.getId());
        return user;
    }

    public User addUser(String lobbyId, User user) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
        Duration ttl = lobbyRepository.getRemainingTtl(lobbyId).orElse(LobbyRepository.SESSION_TTL);
        if (user.getId() == null || user.getId().isBlank()) {
            user.setId(UUID.randomUUID().toString());
        }
        user.setLobbyId(lobbyId);
        user = userRepository.saveWithTtl(user, ttl);
        lobbyRepository.addUser(lobby.getId(), user.getId());
        return user;
    }

    public List<Lobby> getLobbiesByStatus(LobbyStatus status){
        return lobbyRepository.findAllByStatus(status);
    }

    public Lobby getLobby(String lobbyId) {
        return lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
    }

    public List<User> getVisibleLobbyUsers(String lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
        return userRepository.findVisibleByIds(lobby.getUserIds());
    }

    public Lobby startGame(String lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
        lobby.setStatus(LobbyStatus.GAME);
        lobbyRepository.updateStatus(lobbyId, LobbyStatus.GAME);
        lobbyRepository.extendGameTtl(lobbyId);
        return lobby;
    }

    private String generateUniqueLobbyId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String lobbyId = LobbyCodeGenerator.generate();
            if (lobbyRepository.findById(lobbyId).isEmpty()) {
                return lobbyId;
            }
        }
        throw new IllegalStateException("Failed to generate unique lobby id");
    }
}

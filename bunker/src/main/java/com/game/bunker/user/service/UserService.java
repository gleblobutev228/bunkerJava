package com.game.bunker.user.service;

import com.game.bunker.user.entity.User;
import com.game.bunker.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class UserService {
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(7200);

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User saveUser(User user) {
        if (user.getId() == null || user.getId().isBlank()) {
            user.setId(UUID.randomUUID().toString());
        }
        if (user.getSurvivorId() == null || user.getSurvivorId().isBlank()) {
            user.setSurvivorId(user.getId());
        }
        return userRepository.saveWithTtl(user, DEFAULT_TTL);
    }

    public User generateAndSaveUser(String lobbyId, String nickname, Duration ttl) {
        String userId = UUID.randomUUID().toString();
        User user = new User(userId, nickname, false, lobbyId, userId);
        return userRepository.saveWithTtl(user, ttl);
    }

    public void setReady(String userId, boolean ready) {
        userRepository.setReady(userId, ready);
    }

    public boolean exists(String userId) {
        return userRepository.existsById(userId);
    }

    public void deleteUser(String userId) {
        userRepository.deleteById(userId);
    }

    public User getUser(String userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new NoSuchElementException("User not found: " + userId));
    }
}

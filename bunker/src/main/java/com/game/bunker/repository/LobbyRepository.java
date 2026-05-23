package com.game.bunker.repository;

import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.LobbyStatus;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Repository
public class LobbyRepository {
    public static final Duration SESSION_TTL = Duration.ofSeconds(7200);

    private final StringRedisTemplate redisTemplate;

    public LobbyRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Lobby save(Lobby lobby) {
        String lobbyKey = lobbyKey(lobby.getId());
        redisTemplate.opsForHash().putAll(lobbyKey, Map.of("status", lobby.getStatus().name().toLowerCase()));

        String usersKey = lobbyUsersKey(lobby.getId());
        if (!lobby.getUserIds().isEmpty()) {
            redisTemplate.opsForSet().add(usersKey, lobby.getUserIds().toArray(String[]::new));
        }

        return lobby;
    }

    public Lobby saveWithInitialTtl(Lobby lobby) {
        save(lobby);
        expireLobbyKeys(lobby.getId(), SESSION_TTL);
        return lobby;
    }

    public Optional<Lobby> findById(String lobbyId) {
        String status = (String) redisTemplate.opsForHash().get(lobbyKey(lobbyId), "status");
        if (status == null) {
            return Optional.empty();
        }

        Set<String> userIds = redisTemplate.opsForSet().members(lobbyUsersKey(lobbyId));
        if (userIds == null) {
            userIds = new HashSet<>();
        }

        return Optional.of(new Lobby(lobbyId, LobbyStatus.valueOf(status.toUpperCase()), userIds));
    }

    public List<Lobby> findAllByStatus(LobbyStatus status) {
        Set<String> keys = redisTemplate.keys("lobby:*");
        if (keys == null) {
            return List.of();
        }

        return keys.stream()
                .filter(key -> !key.endsWith(":users"))
                .map(key -> key.substring("lobby:".length()))
                .map(this::findById)
                .flatMap(Optional::stream)
                .filter(lobby -> lobby.getStatus() == status)
                .collect(Collectors.toList());
    }

    public void addUser(String lobbyId, String userId) {
        redisTemplate.opsForSet().add(lobbyUsersKey(lobbyId), userId);
        getRemainingTtl(lobbyId).ifPresent(ttl -> redisTemplate.expire(lobbyUsersKey(lobbyId), ttl));
    }

    public void updateStatus(String lobbyId, LobbyStatus status) {
        redisTemplate.opsForHash().put(lobbyKey(lobbyId), "status", status.name().toLowerCase());
    }

    public void extendGameTtl(String lobbyId) {
        expireLobbyKeys(lobbyId, SESSION_TTL);
        Set<String> userIds = redisTemplate.opsForSet().members(lobbyUsersKey(lobbyId));
        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        List<String> userKeys = new ArrayList<>();
        for (String userId : userIds) {
            userKeys.add(userKey(userId));
        }
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : userKeys) {
                connection.keyCommands().expire(redisTemplate.getStringSerializer().serialize(key), SESSION_TTL.toSeconds());
            }
            return null;
        });
    }

    public Optional<Duration> getRemainingTtl(String lobbyId) {
        Long seconds = redisTemplate.getExpire(lobbyKey(lobbyId));
        if (seconds == null || seconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(seconds));
    }

    private void expireLobbyKeys(String lobbyId, Duration ttl) {
        redisTemplate.expire(lobbyKey(lobbyId), ttl);
        redisTemplate.expire(lobbyUsersKey(lobbyId), ttl);
    }

    private String lobbyKey(String lobbyId) {
        return "lobby:" + lobbyId;
    }

    private String lobbyUsersKey(String lobbyId) {
        return "lobby:" + lobbyId + ":users";
    }

    private String userKey(String userId) {
        return "user:" + userId;
    }
}

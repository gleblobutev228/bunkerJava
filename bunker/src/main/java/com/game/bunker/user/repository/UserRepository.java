package com.game.bunker.user.repository;

import com.game.bunker.user.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class UserRepository {
    private final StringRedisTemplate redisTemplate;

    public UserRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public User save(User user) {
        redisTemplate.opsForHash().putAll(userKey(user.getId()), toHash(user));
        return user;
    }

    public User saveWithTtl(User user, Duration ttl) {
        save(user);
        redisTemplate.expire(userKey(user.getId()), ttl);
        return user;
    }

    public Optional<User> findById(String userId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(userKey(userId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(fromHash(userId, entries));
    }

    public List<User> findByIds(Iterable<String> userIds) {
        return toStream(userIds)
                .map(this::findById)
                .flatMap(Optional::stream)
                .toList();
    }

    public boolean existsById(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(userKey(userId)));
    }

    public void setReady(String userId, boolean ready) {
        redisTemplate.opsForHash().put(userKey(userId), "ready", Boolean.toString(ready));
    }

    public void deleteById(String userId) {
        redisTemplate.delete(userKey(userId));
    }

    private Map<String, String> toHash(User user) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("nickname", nullToEmpty(user.getNickname()));
        hash.put("ready", Boolean.toString(user.isReady()));
        hash.put("lobby_id", nullToEmpty(user.getLobbyId()));
        hash.put("survivor_id", nullToEmpty(user.getSurvivorId()));
        return hash;
    }

    private User fromHash(String userId, Map<Object, Object> hash) {
        User user = new User();
        user.setId(userId);
        user.setNickname((String) hash.getOrDefault("nickname", ""));
        user.setReady(Boolean.parseBoolean((String) hash.getOrDefault("ready", "false")));
        user.setLobbyId((String) hash.getOrDefault("lobby_id", ""));
        user.setSurvivorId((String) hash.getOrDefault("survivor_id", userId));
        return user;
    }

    private java.util.stream.Stream<String> toStream(Iterable<String> userIds) {
        return java.util.stream.StreamSupport.stream(userIds.spliterator(), false);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private String userKey(String userId) {
        return "user:" + userId;
    }
}

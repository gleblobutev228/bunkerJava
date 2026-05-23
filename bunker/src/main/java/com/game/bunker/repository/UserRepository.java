package com.game.bunker.repository;

import com.game.bunker.entity.User;
import com.game.bunker.entity.UserCharacteristic;
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

    public User saveHashWithTtl(String userId, Map<String, String> hash, Duration ttl) {
        redisTemplate.opsForHash().putAll(userKey(userId), hash);
        redisTemplate.expire(userKey(userId), ttl);
        return fromHash(userId, toObjectMap(hash), false);
    }

    public Optional<User> findById(String userId) {
        return findById(userId, false);
    }

    public Optional<User> findVisibleById(String userId) {
        return findById(userId, true);
    }

    public List<User> findVisibleByIds(Iterable<String> userIds) {
        return toStream(userIds)
                .map(this::findVisibleById)
                .flatMap(Optional::stream)
                .toList();
    }

    public void openCharacteristic(String userId, String charName) {
        redisTemplate.opsForHash().put(userKey(userId), charName + ":visible", "1");
    }

    private Optional<User> findById(String userId, boolean hideInvisible) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(userKey(userId));
        if (entries.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(fromHash(userId, entries, hideInvisible));
    }

    private Map<String, String> toHash(User user) {
        Map<String, String> hash = new LinkedHashMap<>();
        hash.put("nickname", nullToEmpty(user.getNickname()));
        hash.put("ready", Boolean.toString(user.isReady()));
        hash.put("lobby_id", nullToEmpty(user.getLobbyId()));

        for (String name : User.CHARACTERISTIC_NAMES) {
            UserCharacteristic characteristic = user.getCharacteristics() == null ? null : user.getCharacteristics().get(name);
            hash.put(name, characteristic == null ? "" : nullToEmpty(characteristic.getValue()));
            hash.put(name + ":visible", characteristic != null && characteristic.isVisible() ? "1" : "0");
            if (!"bio".equals(name) && characteristic != null && characteristic.getDescription() != null) {
                hash.put(name + ":description", characteristic.getDescription());
            }
        }

        return hash;
    }

    private User fromHash(String userId, Map<Object, Object> hash, boolean hideInvisible) {
        User user = new User();
        user.setId(userId);
        user.setNickname((String) hash.getOrDefault("nickname", ""));
        user.setReady(Boolean.parseBoolean((String) hash.getOrDefault("ready", "false")));
        user.setLobbyId((String) hash.getOrDefault("lobby_id", ""));

        Map<String, UserCharacteristic> characteristics = new LinkedHashMap<>();
        for (String name : User.CHARACTERISTIC_NAMES) {
            String value = (String) hash.getOrDefault(name, "");
            boolean visible = "1".equals(hash.get(name + ":visible"));
            String description = (String) hash.get(name + ":description");
            characteristics.put(name, new UserCharacteristic(hideInvisible && !visible ? null : value, visible, description));
        }
        user.setCharacteristics(characteristics);

        return user;
    }

    private java.util.stream.Stream<String> toStream(Iterable<String> userIds) {
        return java.util.stream.StreamSupport.stream(userIds.spliterator(), false);
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private Map<Object, Object> toObjectMap(Map<String, String> hash) {
        return new LinkedHashMap<>(hash);
    }

    private String userKey(String userId) {
        return "user:" + userId;
    }
}

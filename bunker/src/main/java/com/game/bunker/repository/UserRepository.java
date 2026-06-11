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

/**
 * Redis-репозиторий игроков.
 * Хранит игрока в hash user:{id}, управляет TTL, ready-флагом и видимостью характеристик.
 */
@Repository
public class UserRepository {
    private final StringRedisTemplate redisTemplate;

    public UserRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Сохраняет пользователя в Redis hash без изменения TTL.
     *
     * @param user доменная модель пользователя.
     * @return сохраненный пользователь.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService, LobbyService.
     * - Куда (Outbound): Redis hash user:{id}.
     */
    public User save(User user) {
        // Spring Data Redis Hash используется как плоское хранилище состояния игрока.
        redisTemplate.opsForHash().putAll(userKey(user.getId()), toHash(user));
        return user;
    }

    /**
     * Сохраняет пользователя и выставляет TTL.
     *
     * @param user доменная модель пользователя.
     * @param ttl время жизни Redis-ключа.
     * @return сохраненный пользователь.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.saveUser, LobbyService.addUser.
     * - Куда (Outbound): Redis hash user:{id} и expire.
     */
    // TODO(senior): save и expire не атомарны; при сбое между командами пользователь может остаться без TTL.
    public User saveWithTtl(User user, Duration ttl) {
        save(user);
        redisTemplate.expire(userKey(user.getId()), ttl);
        return user;
    }


    /**
     * Загружает полное состояние пользователя.
     *
     * @param userId идентификатор пользователя.
     * @return Optional с пользователем или empty.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.getUser, reconnect/join проверки.
     * - Куда (Outbound): Redis hash user:{id}.
     */
    public Optional<User> findById(String userId) {
        return findById(userId, false);
    }

    /**
     * Загружает публичное состояние пользователя со скрытыми закрытыми характеристиками.
     *
     * @param userId идентификатор пользователя.
     * @return Optional с публичным представлением пользователя.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.getVisibleUser, LobbyService.getVisibleLobbyUsers.
     * - Куда (Outbound): Redis hash user:{id}.
     */
    public Optional<User> findVisibleById(String userId) {
        return findById(userId, true);
    }

    /**
     * Загружает публичные состояния нескольких пользователей.
     *
     * @param userIds идентификаторы пользователей.
     * @return список найденных публичных пользователей.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.getVisibleLobbyUsers.
     * - Куда (Outbound): Redis hash user:{id} для каждого участника.
     */
    // TODO(senior): N+1 чтение Redis hash растет вместе с числом игроков; заменить на pipeline/batch-метод репозитория.
    public List<User> findVisibleByIds(Iterable<String> userIds) {
        return toStream(userIds)
                .map(this::findVisibleById)
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Проверяет наличие Redis-ключа пользователя.
     *
     * @param userId идентификатор пользователя.
     * @return true, если user:{id} существует.
     *
     * Call Chain:
     * - Откуда (Inbound): WebSocketAuthInterceptor, LobbySessionService, UserService.
     * - Куда (Outbound): Redis hasKey.
     */
    public boolean existsById(String userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(userKey(userId)));
    }

    /**
     * Обновляет флаг готовности игрока.
     *
     * @param userId идентификатор пользователя.
     * @param ready новое значение готовности.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.setReady из WebSocket ready flow.
     * - Куда (Outbound): Redis hash user:{id}.
     */
    public void setReady(String userId, boolean ready) {
        redisTemplate.opsForHash().put(userKey(userId), "ready", Boolean.toString(ready));
    }

    /**
     * Делает характеристику игрока видимой.
     *
     * @param userId идентификатор пользователя.
     * @param charName имя характеристики.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.openCharacteristic.
     * - Куда (Outbound): Redis hash user:{id}.
     */
    // TODO(senior): Метод не проверяет существование пользователя и состояние игры; инвариант открытия характеристики лучше держать ближе к доменной операции.
    public void openCharacteristic(String userId, String charName) {
        redisTemplate.opsForHash().put(userKey(userId), charName + ":visible", "1");
    }

    /**
     * Удаляет Redis-состояние пользователя.
     *
     * @param userId идентификатор пользователя.
     *
     * Call Chain:
     * - Откуда (Inbound): UserService.deleteUser при выходе игрока или админа.
     * - Куда (Outbound): Redis delete user:{id}.
     */
    public void deleteById(String userId) {
        redisTemplate.delete(userKey(userId));
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
            if (hideInvisible && !visible) {
                value = null;
                description = null;
            }
            characteristics.put(name, new UserCharacteristic(value, visible, description));
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

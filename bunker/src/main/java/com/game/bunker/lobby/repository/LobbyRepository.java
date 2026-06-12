package com.game.bunker.lobby.repository;

import com.game.bunker.transport.ws.LobbyChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.game.bunker.lobby.entity.Lobby;
import com.game.bunker.lobby.entity.LobbyStatus;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Redis-репозиторий лобби.
 * Хранит агрегат лобби в hash lobby:{id}, участников в set lobby:{id}:users
 * и управляет TTL всей игровой комнаты.
 */
@Repository
public class LobbyRepository {
    private static final Logger log = LoggerFactory.getLogger(LobbyRepository.class);
    public static final Duration SESSION_TTL = Duration.ofHours(3);
    private static final int CHAT_HISTORY_LIMIT = 50;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final LobbyUniquenessListRepository lobbyUniquenessListRepository;

    public LobbyRepository(StringRedisTemplate redisTemplate,
                           ObjectMapper objectMapper,
                           LobbyUniquenessListRepository lobbyUniquenessListRepository) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.lobbyUniquenessListRepository = lobbyUniquenessListRepository;
    }

    /**
     * Сохраняет hash лобби и set участников без изменения TTL.
     *
     * @param lobby доменная модель лобби.
     * @return сохраненное лобби.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService при создании или обновлении лобби.
     * - Куда (Outbound): Redis hash lobby:{id}, Redis set lobby:{id}:users.
     */
    public Lobby save(Lobby lobby) {
        String lobbyKey = lobbyKey(lobby.getId());
        // Spring Data Redis Hash хранит статус и администратора лобби.
        redisTemplate.opsForHash().putAll(lobbyKey, Map.of(
                "status", lobby.getStatus().name().toLowerCase(),
                "admin_id", nullToEmpty(lobby.getAdminId())
        ));

        String usersKey = lobbyUsersKey(lobby.getId());
        if (!lobby.getUserIds().isEmpty()) {
            // Redis Set исключает дубли userId среди участников.
            redisTemplate.opsForSet().add(usersKey, lobby.getUserIds().toArray(String[]::new));
        }

        return lobby;
    }

    /**
     * Сохраняет новое лобби и выставляет стартовый TTL.
     *
     * @param lobby новое лобби.
     * @return сохраненное лобби.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.createLobby.
     * - Куда (Outbound): Redis hash/set и expire для ключей лобби.
     */
    public Lobby saveWithInitialTtl(Lobby lobby) {
        save(lobby);
        expireLobbyKeys(lobby.getId(), SESSION_TTL);
        return lobby;
    }

    /**
     * Загружает лобби из Redis по коду.
     *
     * @param lobbyId код лобби.
     * @return Optional с лобби или empty, если hash отсутствует.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService, LobbySecurity.
     * - Куда (Outbound): Redis hash lobby:{id}, Redis set lobby:{id}:users.
     */
    // TODO(senior): Метод делает несколько отдельных Redis-запросов; для горячего path лучше читать hash/set через pipeline или объединенный Lua.
    public Optional<Lobby> findById(String lobbyId) {
        // Hash lobby:{id} является признаком существования лобби.
        String status = (String) redisTemplate.opsForHash().get(lobbyKey(lobbyId), "status");
        if (status == null) {
            return Optional.empty();
        }
        String adminId = (String) redisTemplate.opsForHash().get(lobbyKey(lobbyId), "admin_id");

        Set<String> userIds = redisTemplate.opsForSet().members(lobbyUsersKey(lobbyId));
        if (userIds == null) {
            userIds = new HashSet<>();
        }

        return Optional.of(new Lobby(
                lobbyId,
                LobbyStatus.valueOf(status.toUpperCase()),
                adminId,
                userIds
        ));
    }

    /**
     * Возвращает все лобби с указанным статусом.
     *
     * @param status фильтр статуса.
     * @return список найденных лобби.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.getLobbiesByStatus.
     * - Куда (Outbound): Redis keys lobby:* и findById для каждого hash.
     */
    // TODO(senior): KEYS + findById на каждое лобби станет бутылочным горлышком Redis; нужен индекс по статусу или SCAN/pipeline.
    public List<Lobby> findAllByStatus(LobbyStatus status) {
        // Redis keys используется для небольшого учебного проекта; в production лучше индексировать лобби по статусу.
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

    /**
     * Добавляет пользователя в set участников лобби.
     *
     * @param lobbyId код лобби.
     * @param userId идентификатор пользователя.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.addUser.
     * - Куда (Outbound): Redis set lobby:{id}:users и expire.
     */
    // TODO(senior): SADD и выставление TTL выполняются отдельно; при сбое возможен ключ участников без корректного времени жизни.
    public void addUser(String lobbyId, String userId) {
        // Redis Set хранит только идентификаторы участников, сами пользователи лежат в user:{id}.
        redisTemplate.opsForSet().add(lobbyUsersKey(lobbyId), userId);
        getRemainingTtl(lobbyId).ifPresent(ttl -> {
            redisTemplate.expire(lobbyUsersKey(lobbyId), ttl);
            redisTemplate.expire(lobbyChatKey(lobbyId), ttl);
        });
    }

    /**
     * Обновляет статус лобби в Redis.
     *
     * @param lobbyId код лобби.
     * @param status новый статус.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.updateStatus, LobbyService.startGame.
     * - Куда (Outbound): Redis hash lobby:{id}.
     */
    public void updateStatus(String lobbyId, LobbyStatus status) {
        redisTemplate.opsForHash().put(lobbyKey(lobbyId), "status", status.name().toLowerCase());
    }

    /**
     * Назначает администратора лобби.
     *
     * @param lobbyId код лобби.
     * @param adminId userId создателя лобби.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.assignAdminIfMissing.
     * - Куда (Outbound): Redis hash lobby:{id}.
     */
    public void updateAdminId(String lobbyId, String adminId) {
        redisTemplate.opsForHash().put(lobbyKey(lobbyId), "admin_id", adminId);
    }

    /**
     * Удаляет пользователя из set участников.
     *
     * @param lobbyId код лобби.
     * @param userId идентификатор пользователя.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.leaveLobby для обычного игрока.
     * - Куда (Outbound): Redis set lobby:{id}:users.
     */
    public void removeUser(String lobbyId, String userId) {
        redisTemplate.opsForSet().remove(lobbyUsersKey(lobbyId), userId);
    }

    /**
     * Полностью удаляет Redis-состояние лобби.
     *
     * @param lobbyId код лобби.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.leaveLobby при выходе администратора.
     * - Куда (Outbound): Redis delete lobby:{id}, lobby:{id}:users.
     */
    public void deleteLobby(String lobbyId) {
        redisTemplate.delete(List.of(lobbyKey(lobbyId), lobbyUsersKey(lobbyId), lobbyChatKey(lobbyId)));
        lobbyUniquenessListRepository.deleteByLobbyId(lobbyId);
    }

    /**
     * Увеличивает версию перемешивания карт.
     *
     * @param lobbyId код лобби.
     * @return новая версия действия.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.shuffleCards.
     * - Куда (Outbound): Redis hash increment lobby:{id}.
     */
    public long updateCardsShuffleState(String lobbyId) {
        Long version = redisTemplate.opsForHash().increment(lobbyKey(lobbyId), "cards_shuffle_version", 1);
        return version == null ? 0 : version;
    }

    /**
     * Продлевает TTL лобби и всех пользователей внутри.
     *
     * @param lobbyId код лобби.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.startGame.
     * - Куда (Outbound): Redis expire для lobby:{id}, lobby:{id}:users и user:{id}.
     */
    // TODO(senior): members() загружает всех игроков в память; для больших лобби использовать SCAN или Lua/pipeline без полной материализации.
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
        // Pipelined Redis-команды уменьшают количество round-trip при продлении TTL всех игроков.
        redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : userKeys) {
                connection.keyCommands().expire(redisTemplate.getStringSerializer().serialize(key), SESSION_TTL.toSeconds());
            }
            return null;
        });
    }

    /**
     * Возвращает оставшееся время жизни лобби.
     *
     * @param lobbyId код лобби.
     * @return Optional с TTL, если Redis сообщает положительное значение.
     *
     * Call Chain:
     * - Откуда (Inbound): LobbyService.addUser.
     * - Куда (Outbound): Redis TTL lobby:{id}.
     */
    public Optional<Duration> getRemainingTtl(String lobbyId) {
        Long seconds = redisTemplate.getExpire(lobbyKey(lobbyId));
        if (seconds == null || seconds <= 0) {
            return Optional.empty();
        }
        return Optional.of(Duration.ofSeconds(seconds));
    }

    // TODO(senior): Запись, trim и expire истории чата не атомарны; при сбое возможна рассинхронизация списка и TTL.
    public void addChatMessage(String lobbyId, LobbyChatMessage message) {
        try {
            String chatKey = lobbyChatKey(lobbyId);
            ListOperations<String, String> listOperations = redisTemplate.opsForList();
            listOperations.leftPush(chatKey, objectMapper.writeValueAsString(message));
            listOperations.trim(chatKey, 0, CHAT_HISTORY_LIMIT - 1);
            getRemainingTtl(lobbyId).ifPresent(ttl -> redisTemplate.expire(chatKey, ttl));
        } catch (Exception e) {
            log.warn("Failed to store chat message in Redis list for lobbyId={}, reason={}", lobbyId, e.getMessage());
        }
    }

    // TODO(senior): JSON-десериализация каждого сообщения на пути HTTP-запроса может стать точкой задержек; рассмотреть пакетную обработку/кэш или отдельное DTO-хранилище.
    public List<LobbyChatMessage> getChatHistory(String lobbyId) {
        List<LobbyChatMessage> result = new ArrayList<>();
        try {
            List<String> serializedMessages = redisTemplate.opsForList().range(lobbyChatKey(lobbyId), 0, CHAT_HISTORY_LIMIT - 1);
            if (serializedMessages == null || serializedMessages.isEmpty()) {
                return List.of();
            }
            List<String> messagesInReverseChronology = new ArrayList<>(serializedMessages);
            // LPUSH хранит новые сообщения первыми, API чата отдает от старых к новым.
            Collections.reverse(messagesInReverseChronology);
            for (String serializedMessage : messagesInReverseChronology) {
                result.add(objectMapper.readValue(serializedMessage, LobbyChatMessage.class));
            }
            return result;
        } catch (Exception e) {
            log.warn("Failed to load chat history from Redis list for lobbyId={}, reason={}", lobbyId, e.getMessage());
            return result;
        }
    }

    private void expireLobbyKeys(String lobbyId, Duration ttl) {
        redisTemplate.expire(lobbyKey(lobbyId), ttl);
        redisTemplate.expire(lobbyUsersKey(lobbyId), ttl);
        redisTemplate.expire(lobbyChatKey(lobbyId), ttl);
    }

    private String lobbyKey(String lobbyId) {
        return "lobby:" + lobbyId;
    }

    private String lobbyUsersKey(String lobbyId) {
        return "lobby:" + lobbyId + ":users";
    }

    private String lobbyChatKey(String lobbyId) {
        return "lobby:" + lobbyId + ":chat";
    }

    private String userKey(String userId) {
        return "user:" + userId;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

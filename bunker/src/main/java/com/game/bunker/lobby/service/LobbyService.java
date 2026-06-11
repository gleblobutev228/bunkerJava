package com.game.bunker.lobby.service;

import com.game.bunker.transport.ws.LobbyChatMessage;
import com.game.bunker.lobby.entity.Lobby;
import com.game.bunker.lobby.entity.LobbyStatus;
import com.game.bunker.user.entity.User;
import com.game.bunker.user.service.UserService;
import com.game.bunker.lobby.repository.LobbyRepository;
import com.game.bunker.user.repository.UserRepository;
import com.game.bunker.shared.utils.generator.LobbyCodeGenerator;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Доменный сервис управления лобби.
 * Инкапсулирует создание лобби, добавление игроков, назначение администратора,
 * смену статусов, проверку членства и правила удаления Redis-состояния при выходе.
 */
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

    /**
     * Создает новое открытое лобби с уникальным кодом.
     *
     * @return созданное лобби.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): LobbySessionService при создании HTTP-сессии администратора.
     * - Куда (Outbound): LobbyRepository и Redis hash/set с TTL.
     */
    public Lobby createLobby() {
        String lobbyId = generateUniqueLobbyId();
        Lobby lobby = new Lobby();
        lobby.setId(lobbyId);
        lobby.setStatus(LobbyStatus.OPEN);
        return lobbyRepository.saveWithInitialTtl(lobby);
    }

    /**
     * Создает нового игрока в существующем лобби.
     *
     * @param lobbyId код лобби.
     * @param nickname отображаемое имя игрока.
     * @return созданный игрок с сгенерированными характеристиками.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): LobbySessionService join или create lobby flow.
     * - Куда (Outbound): UserService, UserRepository, LobbyRepository, Redis TTL.
     */
    // TODO(senior): Гонка join/admin assignment: создание игрока, добавление в set и назначение админа не атомарны; перенести правило в Redis transaction/Lua.
    public User addUser(String lobbyId, String nickname) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
        // TTL игрока синхронизируется с TTL лобби в Redis, чтобы состояние истекало согласованно.
        Duration ttl = lobbyRepository.getRemainingTtl(lobbyId).orElse(LobbyRepository.SESSION_TTL);
        User user = userService.generateAndSaveUser(lobbyId, nickname, ttl);
        lobbyRepository.addUser(lobby.getId(), user.getId());
        assignAdminIfMissing(lobby, user.getId());
        return user;
    }

    /**
     * Добавляет заранее созданного пользователя в лобби.
     *
     * @param lobbyId код лобби.
     * @param user объект пользователя.
     * @return сохраненный пользователь.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): внутренние сценарии или тесты, где User уже собран.
     * - Куда (Outbound): UserRepository, LobbyRepository, Redis.
     */
    // TODO(senior): Дублирует сценарий addUser(String, String), из-за чего бизнес-правила могут разойтись; выделить единый путь изменения агрегата.
    public User addUser(String lobbyId, User user) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
        // TTL Redis-ключа пользователя наследует оставшееся время жизни лобби.
        Duration ttl = lobbyRepository.getRemainingTtl(lobbyId).orElse(LobbyRepository.SESSION_TTL);
        if (user.getId() == null || user.getId().isBlank()) {
            user.setId(UUID.randomUUID().toString());
        }
        user.setLobbyId(lobbyId);
        user = userRepository.saveWithTtl(user, ttl);
        lobbyRepository.addUser(lobby.getId(), user.getId());
        assignAdminIfMissing(lobby, user.getId());
        return user;
    }

    /**
     * Возвращает лобби по статусу.
     *
     * @param status статус лобби.
     * @return список лобби с указанным статусом.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): MainController для страницы списка лобби.
     * - Куда (Outbound): LobbyRepository и Redis scan по lobby:*.
     */
    public List<Lobby> getLobbiesByStatus(LobbyStatus status){
        return lobbyRepository.findAllByStatus(status);
    }

    /**
     * Загружает лобби по коду или выбрасывает ошибку.
     *
     * @param lobbyId код лобби.
     * @return найденное лобби.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): контроллеры, security expressions и сервисы.
     * - Куда (Outbound): LobbyRepository, Redis.
     */
    public Lobby getLobby(String lobbyId) {
        return lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
    }

    /**
     * Возвращает игроков лобби с учетом скрытых характеристик.
     *
     * @param lobbyId код лобби.
     * @return список игроков в публичном представлении.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketService после изменения ready.
     * - Куда (Outbound): LobbyRepository, UserRepository, Redis.
     */
    // TODO(senior): Потенциальный N+1 к Redis через findVisibleByIds; для роста лобби нужен batch/pipeline на уровне репозитория.
    public List<User> getVisibleLobbyUsers(String lobbyId) {
        Lobby lobby = lobbyRepository.findById(lobbyId)
                .orElseThrow(() -> new NoSuchElementException("Lobby not found: " + lobbyId));
        return userRepository.findVisibleByIds(lobby.getUserIds());
    }

    /**
     * Меняет статус лобби от имени администратора.
     *
     * @param lobbyId код лобби.
     * @param status новый статус.
     * @param userId текущий пользователь.
     * @return обновленное лобби.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketService admin status command.
     * - Куда (Outbound): LobbyRepository, Redis lobby hash.
     */
    // TODO(senior): Проверка прав и изменение статуса разделены по Redis-вызовам; при конкуренции состояние может измениться между ними.
    public Lobby updateStatus(String lobbyId, LobbyStatus status, String userId) {
        Lobby lobby = requireAdmin(lobbyId, userId);
        lobby.setStatus(status);
        lobbyRepository.updateStatus(lobbyId, status);
        return lobby;
    }

    /**
     * Запускает игру и продлевает TTL Redis-состояния.
     *
     * @param lobbyId код лобби.
     * @param userId текущий пользователь, который должен быть администратором.
     * @return обновленное лобби.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): LobbySessionService или GameWebSocketService start command.
     * - Куда (Outbound): LobbyRepository, Redis status hash и expire для лобби/игроков.
     */
    // TODO(senior): Переход в игру и продление TTL выполняются несколькими Redis-командами; сделать startGame атомарным и идемпотентным.
    public Lobby startGame(String lobbyId, String userId) {
        Lobby lobby = requireAdmin(lobbyId, userId);
        lobby.setStatus(LobbyStatus.GAME);
        lobbyRepository.updateStatus(lobbyId, LobbyStatus.GAME);
        lobbyRepository.extendGameTtl(lobbyId);
        return lobby;
    }

    /**
     * Фиксирует админское действие перемешивания карт.
     *
     * @param lobbyId код лобби.
     * @param userId текущий пользователь, который должен быть администратором.
     * @return версия действия в Redis.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): GameWebSocketService shuffle-cards command.
     * - Куда (Outbound): LobbyRepository и Redis hash increment.
     */
    public long shuffleCards(String lobbyId, String userId) {
        requireAdmin(lobbyId, userId);
        return lobbyRepository.updateCardsShuffleState(lobbyId);
    }

    /**
     * Проверяет, является ли пользователь администратором лобби.
     *
     * @param lobbyId код лобби.
     * @param userId проверяемый пользователь.
     * @return true, если userId совпадает с admin_id.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): LobbySecurity для @PreAuthorize.
     * - Куда (Outbound): LobbyRepository через getLobby.
     */
    public boolean isAdmin(String lobbyId, String userId) {
        Lobby lobby = getLobby(lobbyId);
        return lobby.getAdminId() != null && lobby.getAdminId().equals(userId);
    }

    /**
     * Возвращает лобби, если текущий пользователь администратор.
     *
     * @param lobbyId код лобби.
     * @param userId текущий пользователь.
     * @return лобби при успешной проверке прав.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): доменные методы админских действий.
     * - Куда (Outbound): LobbyRepository через getLobby, Spring Security AccessDeniedException.
     */
    public Lobby requireAdmin(String lobbyId, String userId) {
        Lobby lobby = getLobby(lobbyId);
        if (lobby.getAdminId() == null || !lobby.getAdminId().equals(userId)) {
            throw new org.springframework.security.access.AccessDeniedException("Only lobby admin can perform this action");
        }
        return lobby;
    }

    // TODO(senior): Метод пробрасывает репозиторий без доменной валидации/лимитов; при росте чата правила хранения лучше держать в домене или отдельном ChatService.
    public void addChatMessage(String lobbyId, LobbyChatMessage message) {
        lobbyRepository.addChatMessage(lobbyId, message);
    }

    // TODO(senior): История чата читается синхронно из Redis на HTTP path; при активных лобби нужен кэш/пагинация/лимиты на уровне API.
    public List<LobbyChatMessage> getChatHistory(String lobbyId) {
        return lobbyRepository.getChatHistory(lobbyId);
    }

    /**
     * Проверяет членство пользователя в лобби.
     *
     * @param lobbyId код лобби.
     * @param userId проверяемый пользователь.
     * @return true, если пользователь входит в Redis set участников лобби.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): LobbySecurity для @PreAuthorize.
     * - Куда (Outbound): LobbyRepository через getLobby.
     */
    public boolean isMember(String lobbyId, String userId) {
        return getLobby(lobbyId).getUserIds().contains(userId);
    }

    /**
     * Удаляет пользователя из лобби по явной кнопке выхода.
     *
     * @param lobbyId код лобби.
     * @param userId выходящий пользователь.
     * @return результат выхода с признаком выхода администратора и списком затронутых игроков.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): LobbySessionService leave flow.
     * - Куда (Outbound): UserService, UserRepository, LobbyRepository, Redis delete/remove.
     */
    // TODO(senior): Выход админа удаляет пользователей и лобби несколькими вызовами; частичный сбой оставит сиротское состояние, нужна транзакция/Lua.
    public LeaveLobbyResult leaveLobby(String lobbyId, String userId) {
        Lobby lobby = getLobby(lobbyId);
        if (!lobby.getUserIds().contains(userId)) {
            throw new NoSuchElementException("User is not connected to lobby: " + lobbyId);
        }

        List<String> affectedUserIds = List.copyOf(lobby.getUserIds());
        if (userId.equals(lobby.getAdminId())) {
            for (String playerId : affectedUserIds) {
                userService.deleteUser(playerId);
            }
            // Redis удаляет hash лобби и set участников, потому что админ завершает комнату целиком.
            lobbyRepository.deleteLobby(lobbyId);
            return new LeaveLobbyResult(true, affectedUserIds);
        }

        userService.deleteUser(userId);
        // Redis set участников обновляется только для обычного игрока; лобби остается жить.
        lobbyRepository.removeUser(lobbyId, userId);
        return new LeaveLobbyResult(false, List.of(userId));
    }

    // TODO(senior): Проверка уникальности кода через findById не атомарна; при параллельном создании лучше резервировать код SETNX.
    private String generateUniqueLobbyId() {
        for (int attempt = 0; attempt < 20; attempt++) {
            String lobbyId = LobbyCodeGenerator.generate();
            if (lobbyRepository.findById(lobbyId).isEmpty()) {
                return lobbyId;
            }
        }
        throw new IllegalStateException("Failed to generate unique lobby id");
    }

    private void assignAdminIfMissing(Lobby lobby, String userId) {
        if (lobby.getAdminId() == null || lobby.getAdminId().isBlank()) {
            lobby.setAdminId(userId);
            lobbyRepository.updateAdminId(lobby.getId(), userId);
        }
    }

    /**
     * Результат выхода из лобби.
     * Хранит информацию для HTTP/WebSocket слоя: нужно ли очистить cookie всем игрокам или только текущему.
     *
     * @param adminLeft true, если вышел администратор и лобби удалено полностью.
     * @param affectedUserIds пользователи, которым нужно отправить команду очистки JWT.
     */
    public record LeaveLobbyResult(boolean adminLeft, List<String> affectedUserIds) {
    }
}

# Архитектурный обзор проекта

Дата обзора: 2026-06-03

Цель документа - собрать текущие и потенциальные проблемы структуры проекта и показать, где можно применить тот же подход, который обсуждался для `UserService`: вынести генерацию, маппинг, transport-детали или инфраструктурную логику в отдельные компоненты.

## Краткий вывод

Проект уже разделен на понятные пакеты: `controller`, `service`, `repository`, `security`, `dto`, `entity`. Для текущего размера это рабочая структура. Главная проблема не в названиях пакетов, а в том, что несколько классов стали слишком много знать о соседних слоях:

- `UserService` одновременно управляет пользователем и генерирует персонажа.
- `LobbySessionService` содержит бизнес-сценарии, HTTP `ResponseEntity`, cookie и redirect.
- `GameWebSocketService` смешивает игровые действия, работу с `Principal`, sanitize чата и публикацию событий.
- `RedisPubSubService` одновременно publisher, retry-механизм, consumer, deserializer и маршрутизатор STOMP-сообщений.
- Redis-схемы (`user:{id}`, `lobby:{id}`, `lobby:{id}:users`, поля hash) описаны прямо в репозиториях и частично дублируются между слоями.

Самый полезный первый шаг - привести к единому виду генерацию пользователя и Redis-маппинг: генератор должен возвращать доменную модель `User`, а запись в Redis должна идти через один mapper/репозиторийный путь.

## 1. `UserService`: генерация персонажа внутри lifecycle-сервиса

Файл: `bunker/src/main/java/com/game/bunker/service/UserService.java`

### Что сейчас

`UserService` делает две разные работы:

- lifecycle пользователя: `saveUser`, `getUser`, `getVisibleUser`, `setReady`, `openCharacteristic`, `deleteUser`;
- генерация персонажа: выбор профессии, пола, возраста, стажа, характеристик из каталогов, проверка стажа regex-ами, сбор Redis hash.

Это похоже на твою идею: открыть/закрыть/получить пользователя оставить в `UserService`, а генерацию вынести отдельно.

### Почему это проблема

- Сложно тестировать генерацию отдельно от Redis-сохранения.
- Изменение правил генерации трогает сервис, который отвечает еще и за runtime-состояние игрока.
- Сейчас генерация собирает `Map<String, String>` напрямую и вызывает `saveHashWithTtl`, обходя обычный путь `User -> toHash`.
- Redis-схема пользователя может разъехаться между `UserService.buildGeneratedUserHash` и `UserRepository.toHash/fromHash`.

### Варианты решения

Вариант A, минимальный:

- Создать `UserCharacterGenerator` или `CharacterGenerationService`.
- Перенести туда методы генерации: `buildGeneratedUserHash`, `putProfession`, `putBio`, `loadRandomCatalogsByType`, `generateBioAndValidExperience`, `extractExperienceYears`.
- `UserService.generateAndSaveUser` оставить как orchestration: сгенерировать данные, сохранить через репозиторий.

Минус: если генератор продолжит возвращать `Map<String, String>`, дублирование Redis-схемы останется.

Вариант B, лучше:

- Генератор возвращает полноценный `User`.
- `UserRepository.saveWithTtl(user, ttl)` сам превращает `User` в Redis hash.
- Удалить или сузить `saveHashWithTtl`, чтобы не было второго пути записи пользователя.

Такой вариант лучше защищает от расхождения схемы Redis.

Вариант C, более чистый:

- `CharacterGenerationService` генерирует доменный объект/record, например `GeneratedCharacter`.
- `UserFactory` собирает `User` из `nickname`, `lobbyId`, `GeneratedCharacter`.
- `UserService` только вызывает factory/generator и сохраняет результат.

Это имеет смысл, если правила генерации будут расти.

## 2. `UserRepository`: маппинг Redis нормален, но visibility - спорная граница

Файл: `bunker/src/main/java/com/game/bunker/repository/UserRepository.java`

### Что сейчас

`toHash` и `fromHash` находятся внутри Redis-репозитория. Это нормально: репозиторий знает формат хранения Redis.

Спорные места:

- `findVisibleById` и `findVisibleByIds` прячут закрытые характеристики через параметр `hideInvisible`.
- `openCharacteristic` напрямую пишет `{charName}:visible = 1`.
- `saveHashWithTtl` принимает уже готовый Redis hash от внешнего сервиса.

### Почему это проблема

- Репозиторий начинает отвечать не только за хранение, но и за presentation/view-правило: что скрывать от клиента.
- Любой вызов `openCharacteristic` обходит доменные проверки, если их забудут сделать выше.
- `saveHashWithTtl` позволяет другим слоям знать внутреннюю Redis-схему.

### Варианты решения

Вариант A, оставить как есть:

- При текущем размере проекта это допустимо.
- `toHash/fromHash` приватные, схема Redis локализована в одном классе.

Вариант B, вынести mapper:

- Создать `UserRedisMapper`.
- Методы: `toHash(User user)`, `fromHash(String userId, Map<Object, Object> hash)`.
- `UserRepository` останется ответственным только за Redis-команды.

Это полезно, если mapper нужно покрывать отдельными unit-тестами или использовать в нескольких репозиториях.

Вариант C, вынести visibility policy:

- `UserRepository.findById` всегда возвращает полную модель.
- `UserVisibilityService` или `UserViewMapper` строит публичное представление.
- `UserService.getVisibleUser` применяет эту политику.

Это чище архитектурно, но добавляет слой. Я бы делал это после выноса генерации.

## 3. `LobbyService`: слишком много сценариев вокруг Redis-состояния

Файл: `bunker/src/main/java/com/game/bunker/service/LobbyService.java`

### Что сейчас

`LobbyService` управляет созданием лобби, join-flow, статусами, стартом игры, админскими действиями, чатом, выходом игроков и проверками прав.

Особенно заметные места:

- два overload-метода `addUser`, которые могут разойтись по правилам;
- `generateUniqueLobbyId` проверяет уникальность через `findById` в цикле;
- `leaveLobby` удаляет пользователей и лобби несколькими отдельными Redis-командами;
- `addChatMessage` и `getChatHistory` просто прокидывают вызовы в `LobbyRepository`;
- `requireAdmin` бросает `org.springframework.security.access.AccessDeniedException`, то есть доменный сервис знает про Spring Security.

### Почему это проблема

- Join/leave/start не атомарны: при сбое между Redis-командами могут появиться сиротские `user:{id}` или участники в set без пользователя.
- Проверка уникальности кода лобби не защищает от гонки при параллельном создании.
- Чат пока выглядит как отдельная область поведения, но живет внутри lobby-сервиса.
- Spring Security exception в доменном сервисе привязывает бизнес-слой к web/security framework.

### Варианты решения

Вариант A, умеренный:

- Оставить `LobbyService` главным orchestration-сервисом.
- Вынести чат в `ChatService`.
- Вынести генерацию/резервацию кода в `LobbyCodeReservationService`.
- Заменить Spring Security exception на доменное исключение, например `NotLobbyAdminException`.

Вариант B, через команды:

- Ввести command-объекты: `JoinLobbyCommand`, `LeaveLobbyCommand`, `StartGameCommand`.
- Сделать один публичный join-path вместо двух разных `addUser`.
- Overload с готовым `User` оставить только для тестов или удалить.

Вариант C, для надежности Redis:

- Критичные операции `join`, `leave`, `startGame` оформить через Lua scripts или Redis transaction.
- Репозитории должны возвращать результат атомарной операции, а сервис - интерпретировать его.

Это самый полезный путь, если проект будет запускаться в несколько инстансов или под реальной нагрузкой.

## 4. `LobbyRepository`: storage-логика смешана с индексами, чатом и чужими ключами

Файл: `bunker/src/main/java/com/game/bunker/repository/LobbyRepository.java`

### Что сейчас

Репозиторий хранит:

- hash `lobby:{id}`;
- set `lobby:{id}:users`;
- list `lobby:{id}:chat`;
- TTL для lobby/user/chat ключей;
- JSON-сериализацию чата;
- поиск лобби по статусу через `keys("lobby:*")`;
- продление TTL пользователей через локальный метод `userKey`.

### Почему это проблема

- `KEYS lobby:*` блокирующая команда и плохо масштабируется.
- `LobbyRepository` знает схему ключей пользователей (`user:{id}`), хотя это зона `UserRepository`.
- Chat JSON и правила истории чата живут в lobby-репозитории.
- Redis key naming дублируется в нескольких местах.

### Варианты решения

Вариант A, минимальный:

- Вынести генерацию Redis-ключей в `RedisKeyNames`.
- `LobbyRepository` больше не должен сам собирать `user:{id}`.
- Добавить метод `UserRepository.extendTtlForUsers(Collection<String> userIds, Duration ttl)`.

Вариант B, разделить репозитории:

- `LobbyRepository` - только hash/set лобби.
- `LobbyChatRepository` - list истории чата и JSON mapper.
- `UserRepository` - user hash и TTL пользователей.

Вариант C, улучшить поиск:

- Вместо `KEYS lobby:*` вести Redis set-индексы:
  - `lobbies:status:open`;
  - `lobbies:status:close`;
  - `lobbies:status:game`.
- При смене статуса перемещать lobbyId между индексами.
- Для MVP можно заменить `KEYS` на `SCAN`, но индекс будет правильнее.

## 5. `LobbySessionService`: HTTP/cookie детали внутри сервиса

Файл: `bunker/src/main/java/com/game/bunker/service/LobbySessionService.java`

### Что сейчас

Сервис возвращает `ResponseEntity`, собирает `Set-Cookie`, делает redirect, читает cookies из `HttpServletRequest`, вызывает доменный leave и публикует WebSocket-команды очистки JWT.

### Почему это проблема

- Сервис сложно переиспользовать вне HTTP-контроллера.
- Cookie flags (`secure(false)`, `sameSite`, `path`) захардкожены.
- Выход из лобби смешивает доменный сценарий, HTTP response и WebSocket fan-out.

### Варианты решения

Вариант A, тонкая правка:

- Создать `JwtCookieFactory`.
- Перенести туда `createJwtCookie` и `expiredJwtCookie`.
- Значения `secure`, `sameSite`, `path`, `maxAge` брать из configuration properties.

Вариант B, чище по слоям:

- `LobbySessionService` возвращает result DTO:
  - `AuthResponse`;
  - нужен ли новый JWT;
  - нужен ли redirect;
  - нужно ли очистить cookie.
- `HttpLobbyController` сам собирает `ResponseEntity`.

Вариант C, разделить transport:

- `LobbySessionService` - только session/join/reconnect.
- `LobbyLeaveNotifier` или `ClientSessionCommandPublisher` - уведомления клиентам через WebSocket.
- HTTP cookie cleanup остается в контроллере или `JwtCookieFactory`.

## 6. `GameWebSocketService`: transport, domain и publishing в одном классе

Файл: `bunker/src/main/java/com/game/bunker/service/GameWebSocketService.java`

### Что сейчас

Сервис:

- принимает `Principal`;
- достает текущего пользователя;
- валидирует и парсит входные строки;
- экранирует чат;
- вызывает доменные сервисы;
- публикует Redis/STOMP события;
- собирает payload DTO.

### Почему это проблема

- Сервис сложно тестировать без WebSocket/security контекста.
- Правила чата (`MAX_CHAT_MESSAGE_LENGTH`, html escape) нельзя переиспользовать для HTTP API.
- Изменение транспорта публикации затрагивает игровую логику.
- State change и publish почти везде идут разными шагами: состояние может измениться, а событие не уйти.

### Варианты решения

Вариант A, простой:

- В контроллере доставать `userId` из `Principal`.
- В `GameWebSocketService` передавать уже `userId`, а не `Principal`.
- Вынести `sanitizeChatMessage` в `ChatMessageSanitizer`.
- Вынести `parseLobbyStatus` в валидируемый DTO или отдельный parser.

Вариант B, разделить application service и publisher:

- `GameActionService` меняет состояние.
- `GameEventPublisher` публикует события.
- `GameWebSocketService` только связывает входящую STOMP-команду с application service.

Вариант C, надежная доставка:

- Ввести outbox/очередь событий.
- Сначала записывать событие рядом с изменением состояния, потом отдельный worker публикует его.
- Для текущего проекта можно упростить: после reconnect клиент всегда перечитывает актуальное состояние из Redis.

## 7. `RedisPubSubService`: слишком широкий инфраструктурный сервис

Файл: `bunker/src/main/java/com/game/bunker/service/RedisPubSubService.java`

### Что сейчас

Класс одновременно:

- публикует best-effort chat;
- публикует critical events с retry;
- блокирует поток через `Thread.sleep`;
- слушает Redis Pub/Sub;
- десериализует payload через `switch`;
- маршрутизирует события в `SimpMessagingTemplate`;
- хранит счетчики метрик.

### Почему это проблема

- Добавление нового типа события требует менять `switch`.
- Critical retry блокирует вызывающий поток.
- Redis Pub/Sub не имеет ack, dedup и хранения сообщений.
- Один класс трудно тестировать по частям.

### Варианты решения

Вариант A, минимальный:

- Вынести `sleep/retry` в `WsPublishRetryPolicy` или использовать `RetryTemplate`.
- Вынести `deserializePayload` в `RedisWsPayloadMapper`.

Вариант B, разделить по ролям:

- `RedisWsPublisher`;
- `RedisWsSubscriber`;
- `RedisWsEnvelopeMapper`;
- `StompWsEventDispatcher`.

Вариант C, если нужна надежность:

- Перейти с Redis Pub/Sub на Redis Streams или внешний broker.
- Добавить idempotency по `messageId`.
- Для client recovery держать authoritative state в Redis и отдавать snapshot после reconnect.

## 8. Security и авторизация лобби

Файлы:

- `bunker/src/main/java/com/game/bunker/security/LobbySecurity.java`
- `bunker/src/main/java/com/game/bunker/config/SecurityConfig.java`
- `bunker/src/main/java/com/game/bunker/security/JwtProvider.java`
- `bunker/src/main/java/com/game/bunker/security/JwtFilter.java`
- `bunker/src/main/java/com/game/bunker/security/WebSocketAuthInterceptor.java`

### Что сейчас

- `SecurityConfig` разрешает `anyRequest().permitAll()`, а защита держится в основном на `@PreAuthorize`.
- `LobbySecurity` при составных выражениях несколько раз читает лобби из Redis.
- `JwtProvider` имеет default secret в коде.
- HTTP JWT-filter проверяет валидность токена, но не проверяет существование Redis-сессии пользователя.
- WebSocket CONNECT, наоборот, проверяет `userService.exists(userId)`.

### Почему это проблема

- Если забыть `@PreAuthorize` на новом endpoint, он будет открыт.
- На горячих WebSocket-командах один message может делать несколько Redis round-trip только для authorization.
- Поведение HTTP и WebSocket отличается: HTTP может принять JWT пользователя, чья Redis-сессия уже истекла.
- Default secret легко случайно оставить в production.

### Варианты решения

Вариант A, минимальный:

- В `SecurityConfig` явно описать публичные endpoint'ы и защищенные `/api/**`.
- Вынести JWT secret в обязательную настройку без default.
- Cookie `secure` и TTL вынести в config properties.

Вариант B, оптимизировать lobby auth:

- В `LobbySecurity` сделать один метод под действие, например `canSendLobbyMessage(lobbyId, userId)`.
- Метод один раз читает lobby snapshot и проверяет member/admin/status.
- Для частых проверок добавить короткий cache на время request/message.

Вариант C, унифицировать session policy:

- Либо HTTP тоже проверяет `userService.exists(userId)` для защищенных endpoint'ов.
- Либо WebSocket не делает exists на CONNECT, а проверка идет на уровне команд.
- Главное - выбрать один контракт и описать его.

## 9. Entity и DTO: доменная модель протекает в transport/security

Файлы:

- `bunker/src/main/java/com/game/bunker/entity/User.java`
- `bunker/src/main/java/com/game/bunker/entity/Lobby.java`
- `bunker/src/main/java/com/game/bunker/dto/AuthResponse.java`
- `bunker/src/main/java/com/game/bunker/dto/ws/*`

### Что сейчас

- `User implements UserDetails`.
- `getPassword()` возвращает пустую строку.
- `getUsername()` возвращает `nickname`, хотя Spring authentication name в проекте обычно userId.
- DTO ответов и WS-сообщений местами включают `User`/`Lobby` напрямую.
- `Lobby` возвращает mutable `Set<String> userIds`.

### Почему это проблема

- Доменная модель смешана с Spring Security.
- При JSON-сериализации `User` может нести лишние security-поля (`authorities`, `password`) или вести себя неожиданно.
- API/WS-контракт становится привязан к внутренней модели.
- Mutable collection позволяет менять состав lobby в памяти без явного persist.

### Варианты решения

Вариант A, минимальный:

- Убедиться, что Jackson не отдает лишние поля `UserDetails`.
- Не использовать `User` напрямую в новых DTO.

Вариант B, лучше:

- Создать `PlayerPrincipal` или `SecurityUser`, а `User` сделать чистой доменной моделью.
- Для HTTP/WS ответов создать view DTO:
  - `UserView`;
  - `LobbyView`;
  - `LobbyStateView`.

Вариант C, доменная модель строже:

- `Lobby.getUserIds()` возвращает immutable copy.
- Мутации состава идут через методы сервиса/репозитория.

## 10. Каталоги и генерация случайных данных

Файлы:

- `bunker/src/main/java/com/game/bunker/repository/catalog/ExperienceCatalogRepository.java`
- `bunker/src/main/java/com/game/bunker/repository/catalog/ProfessionCatalogRepository.java`
- `bunker/src/main/java/com/game/bunker/repository/catalog/CharacteristicCatalogRepository.java`

### Что сейчас

- Случайные профессии/стаж выбираются через `order by random() limit 1`.
- Валидность стажа вычисляется regex-парсингом русскоязычной строки.
- В `UserService` возможны вложенные retry-циклы.

### Почему это проблема

- `order by random()` плохо масштабируется на больших таблицах.
- Текстовый парсинг стажа хрупкий: изменение формулировки ломает правило.
- Генерация может сделать много запросов к БД.

### Варианты решения

Вариант A, для MVP:

- Оставить как есть, но вынести генерацию в отдельный сервис и покрыть тестами.

Вариант B, структурировать каталог:

- В `ExperienceCatalog` добавить числовое поле `minAge` или `years`.
- Подбирать опыт SQL-запросом по возрасту.

Вариант C, оптимизировать random:

- Загружать нужные каталоги пачкой и выбирать в памяти.
- Или сделать random selection по count/id range.
- Для больших данных использовать отдельный `CatalogRandomSelector`.

## 11. Приоритетный порядок изменений

### Шаг 1. Убрать двойную схему пользователя

Что сделать:

- Создать `CharacterGenerationService`.
- Генератор возвращает `User`, а не `Map<String, String>`.
- `UserService.generateAndSaveUser` сохраняет через `UserRepository.saveWithTtl`.
- Удалить или ограничить `UserRepository.saveHashWithTtl`.

Почему первым:

- Это прямо продолжает твою идею.
- Изменение небольшое по blast radius.
- Сразу убирает риск расхождения Redis hash.

### Шаг 2. Вынести cookie/HTTP из `LobbySessionService`

Что сделать:

- Создать `JwtCookieFactory`.
- Перенести сборку `ResponseEntity` ближе к `HttpLobbyController`.
- `LobbySessionService` пусть возвращает result DTO.

Почему вторым:

- Это заметно улучшит границу controller/service.
- Код станет проще тестировать.

### Шаг 3. Разделить чат

Что сделать:

- Создать `ChatService`.
- Создать `LobbyChatRepository` или хотя бы вынести JSON-маппинг истории чата.
- `GameWebSocketService` использует `ChatService`, а не `LobbyService.addChatMessage`.

Почему третьим:

- Чат уже имеет свои правила: sanitize, лимит длины, история, публикация.
- Это естественный отдельный bounded context внутри проекта.

### Шаг 4. Redis key names и TTL в config

Что сделать:

- Создать `RedisKeyNames`.
- Вынести `SESSION_TTL`, `DEFAULT_TTL`, `TOKEN_TTL` в properties.
- Убрать дублирование `userKey` между репозиториями.

Почему:

- Меньше случайных рассинхронов.
- Легче менять время жизни сессий под окружение.

### Шаг 5. Авторизация лобби одним snapshot

Что сделать:

- В `LobbySecurity` объединить проверки member/admin/status в методы под конкретные действия.
- Уменьшить количество чтений Redis на одну WebSocket-команду.

Почему:

- Это улучшит производительность без изменения бизнес-поведения.

### Шаг 6. Атомарность Redis-сценариев

Что сделать:

- `join`, `leave`, `startGame`, `createLobby` перевести на Lua/transaction там, где важна целостность.
- Резервацию lobby code делать через `SETNX`.

Почему:

- Это уже более серьезный рефакторинг.
- Лучше делать после стабилизации границ сервисов.

## 12. Что не стоит выносить прямо сейчас

- `UserRepository.toHash/fromHash` можно оставить внутри репозитория, пока нет второго потребителя mapper-а. Вынести стоит после того, как генератор перестанет возвращать raw Redis hash.
- `LobbyCodeGenerator` уже маленький и чистый. Его не нужно усложнять; проблема не в генерации строки, а в атомарной резервации кода.
- Простые DTO в `dto/ws` не требуют отдельного слоя mapper-ов, пока они не начинают содержать доменные entity напрямую или сложные правила.
- `ApiExceptionHandler` для REST выглядит нормально; отдельный вопрос - добавить аналогичный контракт ошибок для WebSocket.

## 13. Возможная новая структура пакетов

Один из реалистичных вариантов без чрезмерной архитектуры:

```text
com.game.bunker
├── config
├── controller
├── dto
│   ├── http
│   └── ws
├── entity
├── repository
│   ├── catalog
│   ├── mapper
│   └── redis
├── security
├── service
│   ├── chat
│   ├── generation
│   ├── lobby
│   ├── session
│   └── user
└── util
```

Для текущего проекта можно не переносить все сразу. Достаточно начать с новых компонентов:

- `service/generation/CharacterGenerationService`;
- `repository/mapper/UserRedisMapper`, если понадобится;
- `service/session/JwtCookieFactory`;
- `service/chat/ChatService`;
- `repository/RedisKeyNames`.

## 14. Итоговая рекомендация

Я бы начал с самого близкого к твоему вопросу рефакторинга:

1. Вынести генерацию персонажа из `UserService`.
2. Сделать генератор возвращающим `User`, а не Redis hash.
3. Оставить открытие характеристик, `ready`, `getVisibleUser`, `deleteUser` в `UserService`.
4. После этого посмотреть, нужен ли отдельный `UserRedisMapper`.

Такой шаг улучшит структуру без большого риска и подготовит проект к следующим выносам: cookie/session, chat и Redis key/TTL.

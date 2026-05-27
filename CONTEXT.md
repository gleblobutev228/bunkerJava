# Project Context: Bunker

## 1. System Overview
`Bunker` — серверная часть многопользовательской игры "Бункер" с комнатами (lobby), ролями игроков и пошаговыми игровыми действиями через WebSocket.  
Система разделяет **оперативное игровое состояние** (Redis: лобби, участники, карточки, готовность) и **справочные каталоги генерации персонажа** (PostgreSQL: профессии, опыт, характеристики).  
Основной бизнес-поток: создание лобби -> вход игроков -> генерация персонажей из каталогов -> стадия lobby (ready/chat/status) -> стадия game (открытие характеристик, админ-действия).

## 2. Tech Stack & Environment

| Layer | Technology | Version / Notes | Role |
|---|---|---|---|
| Runtime | Java | `25` (`pom.xml`) | Язык и рантайм приложения |
| Build | Maven | Spring Boot Maven Plugin | Сборка, запуск, dependency management |
| Core Framework | Spring Boot | Parent `4.0.3` | DI, конфигурация, web/security/data экосистема |
| HTTP API | Spring Web MVC | starter `spring-boot-starter-webmvc` | REST/HTTP контроллеры |
| Reactive/Web | Spring WebFlux | starter `spring-boot-starter-webflux` | Реактивная инфраструктура (точечно) |
| Realtime | Spring WebSocket + Messaging | `spring-boot-starter-websocket`, `spring-security-messaging` | STOMP/WebSocket взаимодействие |
| Auth/Security | Spring Security + JWT | `jjwt 0.12.5` | Аутентификация, авторизация, JWT-cookie flow |
| Operational State Store | Redis | `spring-boot-starter-data-redis` | Хранение игровых сессий, TTL, ephemeral state |
| Relational DB | PostgreSQL | runtime driver `org.postgresql` | Хранение каталогов для генерации персонажа |
| ORM (catalog only) | Spring Data JPA | `spring-boot-starter-data-jpa` | Доступ к таблицам catalog |
| Validation | Jakarta Validation | `spring-boot-starter-validation` | Валидация входных DTO |
| Test DB | H2 | test scope | Локальные тестовые сценарии |
| Boilerplate | Lombok | optional | Сокращение шаблонного кода (ограниченно) |

### Environment Baseline

| Key | Default / Source | Purpose |
|---|---|---|
| `spring.datasource.url` | `${POSTGRES_URL:jdbc:postgresql://localhost:5432/bunker}` | Подключение к PostgreSQL |
| `spring.datasource.username` | `${POSTGRES_USER:postgres}` | Пользователь БД |
| `spring.datasource.password` | `${POSTGRES_PASSWORD:postgres}` | Пароль БД |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `jwt.secret` | `${JWT_SECRET:...local-development...}` | Подпись JWT |
| `spring.jpa.hibernate.ddl-auto` | `none` | DDL не автогенерируется Hibernate |
| `spring.jpa.open-in-view` | `false` | Запрещен Open Session in View |

## 3. Architecture & Code Structure

### Architectural Concept
- Стиль: **слоистый монолит** с четким разделением ответственности.
- Потоки:  
  - HTTP: `Controller -> LobbySessionService -> Domain Services -> Repositories`  
  - WebSocket: `GameWebSocketController -> GameWebSocketService -> Domain Services -> Repositories`
- Данные:  
  - Redis для "живого" состояния игры (с TTL).  
  - PostgreSQL для стабильных каталогов генерации.

### Layer Responsibilities

| Layer | Packages | Responsibilities |
|---|---|---|
| Presentation | `controller`, `dto`, `dto.ws` | HTTP/WebSocket endpoint-ы, transport-DTO |
| Application | `service` | Оркестрация сценариев (create/join/start/leave/open/shuffle) |
| Domain Model | `entity`, `entity.catalog` | Игровые агрегаты и catalog entities |
| Persistence | `repository`, `repository.catalog` | Redis-хранилище runtime-state + JPA-репозитории catalog |
| Security | `security`, `config` | JWT, фильтры, handshake interceptors, `@PreAuthorize` проверки |
| Utilities | `utils.generator` | Генерация кода лобби и вспомогательные алгоритмы |

### Mental Map (Folders)

```text
bunker/
  src/main/java/com/game/bunker/
    config/                  # Security/WebSocket config
    controller/              # HTTP + WS controllers
    dto/                     # HTTP DTO
    dto/ws/                  # WS message contracts
    entity/                  # Runtime domain models (Lobby, User, statuses)
    entity/catalog/          # JPA entities for PostgreSQL catalog tables
    repository/              # Redis repositories (runtime state)
    repository/catalog/      # JPA repositories for catalogs
    security/                # JWT, interceptors, security expressions
    service/                 # Business orchestration and game flows
    utils/generator/         # Lobby code generation
  src/main/resources/
    application.properties   # env-dependent config
```

## 4. Database Schema

### 4.1 Persistent Relational Schema (PostgreSQL)

> Примечание: в проекте включен `ddl-auto=none`; схема создается вне Hibernate-автогенерации (миграции/ручной SQL).

#### Table: `profession_catalog`

| Column | Type (logical) | Nullable | Constraints / Notes |
|---|---|---|---|
| `id` | `BIGINT` | No | PK, identity |
| `value` | `VARCHAR` | No | Текст профессии |
| `description` | `TEXT` | Yes | Доп. описание профессии |

#### Table: `experience_catalog`

| Column | Type (logical) | Nullable | Constraints / Notes |
|---|---|---|---|
| `id` | `BIGINT` | No | PK, identity |
| `value` | `VARCHAR` | No | Опыт работы (строка, используется в генерации bio/experience) |

#### Table: `characteristic_catalog`

| Column | Type (logical) | Nullable | Constraints / Notes |
|---|---|---|---|
| `id` | `BIGINT` | No | PK, identity |
| `type` | `VARCHAR` | No | Тип характеристики (`health`,`hobby`,`character`,`phobia`,`info`,`baggage`,`cards`) |
| `value` | `VARCHAR` | No | Значение карточки |
| `description` | `TEXT` | Yes | Расширенное описание карточки |

#### Relational ERD (text)

```text
profession_catalog (independent lookup)
experience_catalog (independent lookup)
characteristic_catalog (independent lookup by type)

No declared FK relations between catalog tables.
All three tables are read as random/value pools during user generation.
```

### 4.2 Runtime State Model (Redis)

#### Keyspace: `user:{userId}` (Hash)

| Field | Type | Meaning |
|---|---|---|
| `nickname` | string | Никнейм игрока |
| `ready` | boolean-string | Готовность игрока |
| `lobby_id` | string | ID лобби |
| `<charName>` | string | Значение характеристики |
| `<charName>:visible` | `"0"`/`"1"` | Флаг видимости характеристики |
| `<charName>:description` | string | Описание характеристики (если есть) |

`charName` из фиксированного набора: `profession`, `bio`, `health`, `hobby`, `character`, `phobia`, `info`, `baggage`, `cards`.

#### Keyspace: `lobby:{lobbyId}` (Hash)

| Field | Type | Meaning |
|---|---|---|
| `status` | string | Статус лобби (lowercase enum) |
| `admin_id` | string | userId администратора |
| `cards_shuffle_version` | integer | Версия админ-действия shuffle |

#### Keyspace: `lobby:{lobbyId}:users` (Set)

| Value | Meaning |
|---|---|
| `userId` | Участник лобби |

#### Runtime ERD (text)

```text
lobby:{id} (hash) 1 --- N lobby:{id}:users (set members userId)
user:{userId} (hash) N --- 1 lobby:{id} via field user.lobby_id

TTL policy:
- initial lobby/session TTL: 3h
- user TTL synchronized to lobby remaining TTL on join/create
- startGame extends TTL for lobby keys + all user keys
```

## 5. Constraints & Development Rules

### Mandatory Coding Rules for AI
- Соблюдать существующую слоистую схему: контроллеры не должны обращаться к репозиториям напрямую.
- Runtime-данные игры хранить только в Redis-репозиториях (`UserRepository`, `LobbyRepository`); не переносить состояние лобби в JPA-сущности.
- Любые проверки прав выполнять через `LobbySecurity`/`@PreAuthorize`; не дублировать ad-hoc auth-логику в контроллерах.
- Любые изменения статуса/членства лобби должны быть согласованы с TTL и keyspace-инвариантами Redis.
- Для новых transport-контрактов использовать отдельные DTO в `dto`/`dto.ws`, не отдавать доменные модели напрямую.
- Ошибки бизнес-уровня выбрасывать как типизированные Java exceptions (`NoSuchElementException`, `IllegalArgumentException`, `AccessDeniedException`) и централизованно обрабатывать в exception handler.
- Поддерживать детерминированный нейминг:  
  - методы-сценарии в сервисах: `create/join/start/leave/update/open/shuffle`;  
  - ключи Redis: префиксы `user:` и `lobby:`.
- Не включать автоматическую DDL-генерацию (`ddl-auto` остается `none`) без отдельного запроса на миграционную стратегию.
- Сохранять совместимость текущих ключей Redis и полей hash (breaking rename запрещен без миграции состояния).

### Known Consistency Risk (must verify before edits)
- В кодовой базе есть потенциальный drift статусов лобби:  
  - `LobbyStatus` содержит `OPEN/CLOSE/GAME`,  
  - часть сервисов/безопасности использует `GAME_STARTED`.  
  При любых правках, затрагивающих статусную модель, сначала привести enum и проверки к единому контракту.

## 6. Common Tasks

| User Intent | Expected AI Action Pattern |
|---|---|
| "Напиши миграцию для catalog таблиц" | Сгенерировать SQL migration для PostgreSQL (`profession_catalog`, `experience_catalog`, `characteristic_catalog`), проверить nullable/PK/indexes |
| "Создай новый HTTP endpoint" | Добавить DTO -> метод в `HttpLobbyController` -> orchestration в `LobbySessionService`/`LobbyService` -> security/validation |
| "Добавь новое WebSocket действие" | Добавить request/response DTO в `dto.ws` -> `@MessageMapping` в `GameWebSocketController` -> бизнес-метод в `GameWebSocketService` |
| "Добавь новую характеристику персонажа" | Обновить whitelist характеристик в `User.CHARACTERISTIC_NAMES` и генерацию в `UserService`, синхронизировать Redis hash-поля и каталоги |
| "Почини авторизацию" | Проверить `JwtProvider`, `JwtFilter`, handshake interceptors, cookie flow и `@PreAuthorize` выражения |
| "Оптимизируй работу лобби" | Проверить Redis key access patterns, TTL продление, pipelining, объем чтений `keys("lobby:*")` |
| "Сделай ревью бизнес-логики" | Проверить инварианты: админ назначается один раз, выход админа удаляет лобби и игроков, членство и стадии валидируются в security |

### Prompt Templates (for future AI sessions)
- `Сгенерируй migration SQL для PostgreSQL под новую колонку в characteristic_catalog + rollback.`
- `Добавь endpoint POST /api/v1/lobbies/{id}/... с валидацией DTO и проверкой прав через @PreAuthorize.`
- `Добавь WebSocket команду для стадии game и отправку broadcast-сообщения всем участникам.`
- `Проверь согласованность enum статусов lobby между service, security и Redis serialization.`
- `Напиши unit/integration тесты для сценария "админ выходит из лобби".`

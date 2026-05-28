# Как запустить проект на новом компьютере

Этот файл для сценария: "только что сделал `git clone` и хочу поднять проект локально".

## 1) Что должно быть установлено

1. **JDK 25**
   - Проект собирается с `java.version=25`.
2. **PostgreSQL 17+**
   - Нужен локальный сервер БД на `localhost:5432`.
3. **Redis**
   - Нужен локальный Redis на `localhost:6379`.

Проверка в PowerShell:

```powershell
java -version
```

## 2) Переход в модуль проекта

Команды выполнять из корня репозитория `D:\bunkerJava`:

```powershell
cd .\bunker
```

Зачем: именно в этой папке лежат `pom.xml` и `mvnw.cmd`.

## 3) Поднять Redis (Windows + winget)

Если Redis еще не установлен:

```powershell
winget install --id Redis.Redis --source winget --accept-package-agreements --accept-source-agreements
```

Проверить, что Redis жив:

```powershell
Test-NetConnection localhost -Port 6379
```

`TcpTestSucceeded` должен быть `True`.

## 4) Подготовить PostgreSQL

### 4.1 Создать БД `bunker` (если ее нет)

```powershell
"C:\Program Files\PostgreSQL\17\bin\createdb.exe" -h localhost -U postgres bunker
```

### 4.2 Сделать пароль пользователя `postgres` = `postgres`

```powershell
"C:\Program Files\PostgreSQL\17\bin\psql.exe" "postgresql://postgres:ВАШ_ТЕКУЩИЙ_ПАРОЛЬ@localhost:5432/postgres" -c "ALTER USER postgres WITH PASSWORD 'postgres';"
```

Зачем: это соответствует `application.properties` проекта по умолчанию.

### 4.3 Создать таблицы и стартовые данные

Из папки `D:\bunkerJava\bunker`:

```powershell
"C:\Program Files\PostgreSQL\17\bin\psql.exe" "postgresql://postgres:postgres@localhost:5432/bunker" -f ".\bootstrap_catalog.sql"
```

Зачем: в проекте стоит `spring.jpa.hibernate.ddl-auto=none`, поэтому схема и каталог-данные должны быть созданы вручную.

## 5) Запуск приложения

Из `D:\bunkerJava\bunker`:

```powershell
.\mvnw.cmd spring-boot:run
```

Успешный старт: в логах есть строка `Started BunkerApplication`.

## 6) Частые проблемы и быстрые решения

### 6.1 Порт 8080 занят

Проверка:

```powershell
netstat -ano | findstr :8080
```

Остановка процесса:

```powershell
taskkill /PID <PID> /F
```

Или запуск на другом порту:

```powershell
.\mvnw.cmd spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

### 6.2 Запуск из неправильной папки

Если видишь ошибку вида `.\mvnw.cmd не распознано`, значит ты не в `D:\bunkerJava\bunker`.

### 6.3 Ошибка подключения к PostgreSQL

Проверь:
- запущен ли сервис PostgreSQL;
- верные ли логин/пароль;
- существует ли БД `bunker`.

### 6.4 Ошибка подключения к Redis

Проверь, что Redis-сервис запущен и порт `6379` доступен.

## 7) Запуск из IDE (IntelliJ/Eclipse)

Запускай `com.game.bunker.BunkerApplication` как Spring Boot application, но:
- проект должен быть импортирован как Maven-проект из `bunker/pom.xml`;
- JDK проекта: **25**.

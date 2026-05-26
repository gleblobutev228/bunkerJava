package com.game.bunker.service;

import com.game.bunker.dto.AuthLoginRequest;
import com.game.bunker.dto.AuthResponse;
import com.game.bunker.dto.UserCreationRequest;
import com.game.bunker.dto.ws.ClientSessionCommandMessage;
import com.game.bunker.entity.User;
import com.game.bunker.security.JwtFilter;
import com.game.bunker.security.JwtProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/**
 * Сервис HTTP-сессий лобби: создание cookie JWT, объединенный join/reconnect и выход из лобби.
 * Отделяет web/session concerns от доменного LobbyService, чтобы контроллеры оставались тонкими,
 * а JWT не превращался в Redis-сессию.
 */
@Service
public class LobbySessionService {
    private final LobbyService lobbyService;
    private final UserService userService;
    private final JwtProvider jwtProvider;
    private final SimpMessagingTemplate messagingTemplate;

    public LobbySessionService(LobbyService lobbyService,
                               UserService userService,
                               JwtProvider jwtProvider,
                               SimpMessagingTemplate messagingTemplate) {
        this.lobbyService = lobbyService;
        this.userService = userService;
        this.jwtProvider = jwtProvider;
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Создает лобби из MVC-формы и формирует redirect с JWT-cookie.
     *
     * @param request данные создателя лобби.
     * @return HTTP redirect на страницу нового лобби.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): HttpLobbyController POST /create.
     * - Куда (Outbound): LobbyService, Redis, JwtProvider, HTTP Set-Cookie.
     */
    public ResponseEntity<Void> createLobbyRedirect(UserCreationRequest request) {
        AuthResponse response = createLobbySession(request.userName());
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, createJwtCookie(response.user()).toString())
                .header(HttpHeaders.LOCATION, "/lobbies/" + response.lobby().getId())
                .build();
    }

    /**
     * Создает лобби через REST API и возвращает состояние с JWT-cookie.
     *
     * @param request данные создателя лобби.
     * @return AuthResponse с пользователем-администратором и Set-Cookie.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): HttpLobbyController POST /api/v1/lobbies.
     * - Куда (Outbound): LobbyService, Redis, JwtProvider.
     */
    public ResponseEntity<AuthResponse> createLobby(UserCreationRequest request) {
        return withJwtCookie(createLobbySession(request.userName()));
    }

    /**
     * Выполняет единый join/reconnect сценарий.
     *
     * @param lobbyId код лобби.
     * @param request данные игрока для создания новой сессии.
     * @param httpRequest HTTP-запрос с потенциальной JWT-cookie.
     * @return существующий пользователь без нового cookie или новый пользователь с Set-Cookie.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): HttpLobbyController POST /api/v1/lobbies/{lobbyId}/join.
     * - Куда (Outbound): JwtProvider, LobbyService, UserService, Redis.
     */
    public ResponseEntity<AuthResponse> joinLobby(String lobbyId, AuthLoginRequest request, HttpServletRequest httpRequest) {
        String userIdFromToken = resolveValidUserId(httpRequest);
        AuthResponse response = joinOrReconnect(lobbyId, request.nickname(), userIdFromToken);
        if (response.user().getId().equals(userIdFromToken)) {
            return ResponseEntity.ok(response);
        }
        return withJwtCookie(response);
    }

    /**
     * Запускает игру из MVC-формы и возвращает redirect.
     *
     * @param lobbyId код лобби.
     * @param authentication текущая Spring Security аутентификация.
     * @return redirect на страницу лобби.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): HttpLobbyController POST /lobbies/{lobbyId}/start.
     * - Куда (Outbound): LobbyService и Redis status/TTL.
     */
    public String startGameRedirect(String lobbyId, Authentication authentication) {
        lobbyService.startGame(lobbyId, currentUserId(authentication));
        return "redirect:/lobbies/" + lobbyId;
    }

    /**
     * Обрабатывает явный выход пользователя из лобби.
     *
     * @param lobbyId код лобби.
     * @param authentication текущая Spring Security аутентификация.
     * @return HTTP 204 с очищением JWT-cookie вызывающего клиента.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): HttpLobbyController POST /api/v1/lobbies/{lobbyId}/leave.
     * - Куда (Outbound): LobbyService, Redis, STOMP /user/queue/reply при выходе админа.
     */
    public ResponseEntity<Void> leaveLobby(String lobbyId, Authentication authentication) {
        String userId = currentUserId(authentication);
        LobbyService.LeaveLobbyResult result = lobbyService.leaveLobby(lobbyId, userId);
        if (result.adminLeft()) {
            notifyPlayersToClearJwt(lobbyId, result);
        }

        return clearJwtCookie();
    }

    private AuthResponse createLobbySession(String adminName) {
        var lobby = lobbyService.createLobby();
        User admin = lobbyService.addUser(lobby.getId(), adminName);
        return new AuthResponse(admin, lobbyService.getLobby(lobby.getId()));
    }

    private AuthResponse joinOrReconnect(String lobbyId, String nickname, String userIdFromToken) {
        var lobby = lobbyService.getLobby(lobbyId);
        if (userIdFromToken != null && lobby.getUserIds().contains(userIdFromToken) && userService.exists(userIdFromToken)) {
            return new AuthResponse(userService.getUser(userIdFromToken), lobby);
        }

        User user = lobbyService.addUser(lobbyId, nickname);
        return new AuthResponse(user, lobbyService.getLobby(lobbyId));
    }

    /**
     * Возвращает ответ для удаления JWT-cookie.
     *
     * @return HTTP 204 с expired BUNKER_JWT cookie.
     *
     *
     * Call Chain:
     * - Откуда (Inbound): HttpLobbyController /api/v1/auth/clear-cookie или leaveLobby.
     * - Куда (Outbound): HTTP Set-Cookie header.
     */
    public ResponseEntity<Void> clearJwtCookie() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, expiredJwtCookie().toString())
                .build();
    }

    private void notifyPlayersToClearJwt(String lobbyId, LobbyService.LeaveLobbyResult result) {
        for (String playerId : result.affectedUserIds()) {
            // STOMP user destination доставляет команду конкретному подключенному игроку.
            messagingTemplate.convertAndSendToUser(
                    playerId,
                    "/queue/reply",
                    new ClientSessionCommandMessage("CLEAR_JWT", lobbyId, "LOBBY_ADMIN_LEFT")
            );
        }
    }

    private ResponseEntity<AuthResponse> withJwtCookie(AuthResponse response) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, createJwtCookie(response.user()).toString())
                .body(response);
    }

    private ResponseCookie createJwtCookie(User user) {
        // ResponseCookie формирует Set-Cookie с HttpOnly JWT для браузера.
        return ResponseCookie.from(JwtFilter.JWT_COOKIE_NAME, jwtProvider.generateToken(user.getId()))
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(JwtProvider.TOKEN_TTL)
                .build();
    }

    private ResponseCookie expiredJwtCookie() {
        // maxAge(0) просит браузер удалить cookie с тем же name/path.
        return ResponseCookie.from(JwtFilter.JWT_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
    }

    private String resolveValidUserId(HttpServletRequest request) {
        String token = resolveJwtCookie(request);
        if (token == null || !jwtProvider.isValid(token)) {
            return null;
        }
        return jwtProvider.getUserId(token);
    }

    private String resolveJwtCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (JwtFilter.JWT_COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private String currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new IllegalArgumentException("JWT token is missing or invalid");
        }
        return authentication.getName();
    }
}

package com.game.bunker.controller;


import com.game.bunker.dto.AuthLoginRequest;
import com.game.bunker.dto.AuthResponse;
import com.game.bunker.dto.LobbyListItemResponse;
import com.game.bunker.dto.UserCreationRequest;
import com.game.bunker.dto.ws.LobbyChatMessage;
import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.service.LobbyService;
import com.game.bunker.service.LobbySessionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

@Controller
public class HttpLobbyController {
    private final LobbySessionService lobbySessionService;
    private final LobbyService lobbyService;

    public HttpLobbyController(LobbySessionService lobbySessionService, LobbyService lobbyService) {
        this.lobbySessionService = lobbySessionService;
        this.lobbyService = lobbyService;
    }

    @PostMapping("/create")
    public ResponseEntity<Void> createLobby(@Valid UserCreationRequest adminName) {
        return lobbySessionService.createLobbyRedirect(adminName);
    }

    @PostMapping("/api/v1/lobbies")
    @ResponseBody
    public ResponseEntity<AuthResponse> createLobbyApi(@Valid @RequestBody UserCreationRequest request) {
        return lobbySessionService.createLobby(request);
    }

    @GetMapping("/api/v1/lobbies")
    @ResponseBody
    // TODO(senior): Парсинг enum в контроллере без обработки ошибки даст 500/общий exception; вынести в DTO/validator и вернуть корректный 400.
    public ResponseEntity<List<LobbyListItemResponse>> getLobbiesByStatus(
            @RequestParam(defaultValue = "OPEN") String status) {
        LobbyStatus lobbyStatus = LobbyStatus.valueOf(status.trim().toUpperCase());
        List<LobbyListItemResponse> response = lobbyService.getLobbiesByStatus(lobbyStatus).stream()
                .map(lobby -> new LobbyListItemResponse(
                        lobby.getId(),
                        lobby.getStatus(),
                        lobby.getAdminId(),
                        lobby.getUserIds().size()
                ))
                .toList();
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/v1/lobbies/{lobbyId}/join")
    @ResponseBody
    public ResponseEntity<AuthResponse> joinLobby(@PathVariable String lobbyId,
                                                  @Valid @RequestBody AuthLoginRequest request,
                                                  HttpServletRequest httpRequest) {
        return lobbySessionService.joinLobby(lobbyId, request, httpRequest);
    }

    @PostMapping("/lobbies/{lobbyId}/start")
    @PreAuthorize("@lobbySecurity.isAdmin(#lobbyId, authentication.name)")
    public String startGame(@PathVariable String lobbyId, Authentication authentication) {
        return lobbySessionService.startGameRedirect(lobbyId, authentication);
    }

    @PostMapping("/api/v1/lobbies/{lobbyId}/leave")
    @PreAuthorize("@lobbySecurity.isMember(#lobbyId, authentication.name)")
    @ResponseBody
    public ResponseEntity<Void> leaveLobby(@PathVariable String lobbyId, Authentication authentication) {
        return lobbySessionService.leaveLobby(lobbyId, authentication);
    }

    @PostMapping("/api/v1/auth/clear-cookie")
    @ResponseBody
    public ResponseEntity<Void> clearJwtCookieForClient() {
        return lobbySessionService.clearJwtCookie();
    }

    @GetMapping("/api/v1/lobbies/{lobbyId}/chat")
    @PreAuthorize("@lobbySecurity.isMember(#lobbyId, authentication.name)")
    @ResponseBody
    public ResponseEntity<List<LobbyChatMessage>> getChatHistory(@PathVariable String lobbyId) {
        return ResponseEntity.ok(lobbyService.getChatHistory(lobbyId));
    }
}

package com.game.bunker.controller;


import com.game.bunker.dto.UserCreationRequest;
import com.game.bunker.service.LobbyService;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class HttpLobbyController {
    private final LobbyService lobbyService;

    public HttpLobbyController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    @PostMapping("/create")
    public String createLobby(@Valid UserCreationRequest adminName){
        var lobby = lobbyService.createLobby(adminName);
        return "redirect:/lobbies/" + lobby.getId();
    }

    @PostMapping("/lobbies/{lobbyId}/start")
    public String startGame(@PathVariable String lobbyId) {
        lobbyService.startGame(lobbyId);
        return "redirect:/lobbies/" + lobbyId;
    }
}

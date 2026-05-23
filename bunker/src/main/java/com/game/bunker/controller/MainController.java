package com.game.bunker.controller;

import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.service.LobbyService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MainController {
    private final LobbyService lobbyService;

    public MainController(LobbyService lobbyService) {
        this.lobbyService = lobbyService;
    }

    @GetMapping("/")
    public String showMainPage(){
        return "main";
    }

    @GetMapping("/lobbies")
    public String showLobbies(Model model){
        model.addAttribute("lobbies", lobbyService.getLobbiesByStatus(LobbyStatus.OPEN));
        return "lobby-list";
    }
}

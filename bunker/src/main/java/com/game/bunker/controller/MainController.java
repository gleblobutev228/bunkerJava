package com.game.bunker.controller;

import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.service.LobbyService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
public class MainController {
    private LobbyService lobbyService;

    @GetMapping()
    public String showMainPage(){
        return "main";
    }

    @GetMapping
    public String showLobbies(Model model){
        model.addAttribute(lobbyService.getLobbiesByStatus(LobbyStatus.OPEN));
        return "lobby-list";
    }
}

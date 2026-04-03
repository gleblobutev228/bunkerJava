package com.game.bunker.controller;


import com.game.bunker.dto.UserCreationRequest;
import com.game.bunker.service.LobbyService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;

@Controller
@AllArgsConstructor
public class HttpLobbyController {
    private LobbyService lobbyService;

    @PostMapping("/create")
    public String createLobby(@Valid UserCreationRequest adminName){
        return "";
    }
}

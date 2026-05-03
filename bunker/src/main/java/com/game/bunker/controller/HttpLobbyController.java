package com.game.bunker.controller;


import com.game.bunker.dto.request.UserCreationRequestDto;
import com.game.bunker.dto.response.LobbyCreationResponseDto;
import com.game.bunker.dto.response.LobbyForListDto;
import com.game.bunker.dto.response.LobbyJoiningResponseDto;
import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.service.LobbyService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/api/lobbies")
@AllArgsConstructor
public class HttpLobbyController {
    private final LobbyService lobbyService;

    @PostMapping("/create")
    public ResponseEntity<LobbyCreationResponseDto> createLobby(@Valid @RequestBody UserCreationRequestDto dto) {
        LobbyCreationResponseDto response = lobbyService.createLobby(dto);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
    @GetMapping
    public ResponseEntity<List<LobbyForListDto>> getOpenLobbies() {
        List<LobbyForListDto> lobbies = lobbyService.getLobbiesByStatus(LobbyStatus.OPEN);

        if (lobbies.isEmpty()) {
            return ResponseEntity.noContent().build(); // 204 если пусто
        }
        return ResponseEntity.ok(lobbies);
    }

//    @PostMapping("/join/{code}")
//    public ResponseEntity<LobbyJoiningResponseDto> joinLobby(@Valid @RequestBody UserCreationRequestDto dto, @PathVariable String code){
//        LobbyJoiningResponseDto response = lobbyService.joinLobby(dto, code);
//
//        return ResponseEntity.status(HttpStatus.CREATED).body(response);
//    }

}

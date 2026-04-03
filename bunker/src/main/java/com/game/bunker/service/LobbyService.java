package com.game.bunker.service;

import com.game.bunker.dto.UserCreationRequest;
import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.repository.LobbyRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class LobbyService {
    private LobbyRepository lobbyRepository;


    public List<Lobby> getLobbiesByStatus(LobbyStatus status){
        return lobbyRepository.findAllByStatus(status);
    };


}

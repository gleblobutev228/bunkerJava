package com.game.bunker.service;

import com.game.bunker.dto.UserSummaryDto;
import com.game.bunker.dto.request.UserCreationRequestDto;
import com.game.bunker.dto.response.LobbyCreationResponseDto;
import com.game.bunker.dto.response.LobbyForListDto;
import com.game.bunker.dto.response.LobbyJoiningResponseDto;
import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.LobbyStatus;
import com.game.bunker.entity.User;
import com.game.bunker.mapper.LobbyMapper;
import com.game.bunker.repository.LobbyRepository;
import com.game.bunker.utils.generator.LobbyCodeGenerator;
import jakarta.persistence.EntityNotFoundException;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import org.springframework.data.crossstore.ChangeSetPersister;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class LobbyService {
    private final LobbyRepository lobbyRepository;
    private final LobbyMapper lobbyMapper;
    private final UserService userService;

    public List<LobbyForListDto> getLobbiesByStatus(LobbyStatus status){
        return lobbyRepository.findAllByStatus(status).stream()
                .map(lobbyMapper::toDto)
                .toList();

    };

    public Lobby getLobbyByCode(String code) {
        return lobbyRepository.findById(code)
                .orElseThrow(() -> new EntityNotFoundException("Lobby with code " + code + " not found"));
    }


    @Transactional
    public LobbyCreationResponseDto createLobby(UserCreationRequestDto dto) {
        //Создаем нового пользователя
        User admin = userService.createUser(dto);
        admin.setRoles(Set.of("ROLE_PLAYER", "ROLE_ADMIN"));
        userService.saveUser(admin);

        //Инициализируем лобби
        Lobby lobby = new Lobby();
        lobby.setCode(LobbyCodeGenerator.generate());
        lobby.setStatus(LobbyStatus.LOCKED);

        //Устанавливаем связи
        lobby.setAdminId(admin);
        lobby.addUser(admin); // Админ также является участником

        lobby = lobbyRepository.save(lobby);

        Set<UserSummaryDto> userDtos = lobby.getUsers().stream()
                .map(user -> new UserSummaryDto(user.getId(), user.getUsername(), user.getRoles()))
                .collect(Collectors.toSet());

        return new LobbyCreationResponseDto(lobby.getCode(), lobby.getStatus().name(), admin.getId(), userDtos);
    }


//    public LobbyJoiningResponseDto joinLobby(UserCreationRequestDto dto, String code){
//        User user = userService.createUser(dto);
//        user.setRoles(Set.of("ROLE_USER"));
//        userService.saveUser(user);
//
//        Lobby lobby = lobbyRepository.findByCode(code)
//                .orElseThrow(() -> new EntityNotFoundException("no such lobby with this code"));
//        lobby.addUser(user);
//        return new LobbyJoiningResponseDto();
//    }


}

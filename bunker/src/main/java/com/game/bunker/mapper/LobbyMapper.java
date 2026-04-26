package com.game.bunker.mapper;

import com.game.bunker.dto.response.LobbyForListDto;
import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;

@Mapper(componentModel = "spring")
public interface LobbyMapper {

    @Mapping(source = "users", target = "playersNumber", qualifiedByName = "countUsers")
    LobbyForListDto toDto(Lobby lobby);

    @Named("countUsers")
    default int countUsers(Set<User> users){
        return users != null ? users.size() : 0;
    }
}

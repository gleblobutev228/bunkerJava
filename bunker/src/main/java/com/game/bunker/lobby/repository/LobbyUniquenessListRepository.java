package com.game.bunker.lobby.repository;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LobbyUniquenessListRepository {
    private final StringRedisTemplate redisTemplate;

    public LobbyUniquenessListRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean addIfAbsent(String lobbyId, Long characteristicId) {
        Long added = redisTemplate.opsForSet().add(uniquenessKey(lobbyId), String.valueOf(characteristicId));
        return added != null && added == 1L;
    }

    public void deleteByLobbyId(String lobbyId) {
        redisTemplate.delete(uniquenessKey(lobbyId));
    }

    private String uniquenessKey(String lobbyId) {
        return "lobby:" + lobbyId + ":characteristics:used";
    }
}

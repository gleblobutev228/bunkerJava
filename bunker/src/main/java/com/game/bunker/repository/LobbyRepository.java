package com.game.bunker.repository;

import com.game.bunker.entity.Lobby;
import com.game.bunker.entity.LobbyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LobbyRepository extends JpaRepository<Lobby, String>{
    List<Lobby> findAllByStatus(LobbyStatus status);
    Optional<Lobby> findByCode(String code);
}

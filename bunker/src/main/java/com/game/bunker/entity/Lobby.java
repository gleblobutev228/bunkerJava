package com.game.bunker.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.HashSet;
import java.util.Set;


@Entity
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "lobby")
public class Lobby {
    @Id
    @Column(length = 6, unique = true, nullable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    private LobbyStatus status;

    @OneToMany(mappedBy = "lobby", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<User> users = new HashSet<>();

    @OneToOne
    @JoinColumn(name = "admin_id", referencedColumnName = "id")
    private User adminId;


    //helper methods
    public void addUser(User user){
        this.users.add(user);
        user.setLobby(this);
    }

    public void removeUser(User user){
        this.users.remove(user);
        user.setLobby(null);
    }
}

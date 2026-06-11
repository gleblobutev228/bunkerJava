package com.game.bunker.user.entity;

import lombok.Getter;
import lombok.Setter;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Setter
@Getter
public class User implements UserDetails {

    private String id;
    private String nickname;
    private boolean ready;
    private String lobbyId;
    private String survivorId;

    public User() {
    }

    public User(String id, String nickname, boolean ready, String lobbyId, String survivorId) {
        this.id = id;
        this.nickname = nickname;
        this.ready = ready;
        this.lobbyId = lobbyId;
        this.survivorId = survivorId;
    }


    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_PLAYER"));
    }

    @Override
    public @Nullable String getPassword() {
        return "";
    }

    @Override
    public String getUsername() {
        return nickname;
    }
}

package com.game.bunker.entity;

import org.jspecify.annotations.Nullable;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class User implements UserDetails {
    public static final List<String> CHARACTERISTIC_NAMES = List.of(
            "profession",
            "bio",
            "health",
            "hobby",
            "character",
            "phobia",
            "info",
            "baggage",
            "cards"
    );

    private String id;
    private String nickname;
    private boolean ready;
    private String lobbyId;
    private Map<String, UserCharacteristic> characteristics = new LinkedHashMap<>();

    public User() {
    }

    public User(String id, String nickname, boolean ready, String lobbyId,
                Map<String, UserCharacteristic> characteristics) {
        this.id = id;
        this.nickname = nickname;
        this.ready = ready;
        this.lobbyId = lobbyId;
        this.characteristics = characteristics;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getLobbyId() {
        return lobbyId;
    }

    public void setLobbyId(String lobbyId) {
        this.lobbyId = lobbyId;
    }

    public Map<String, UserCharacteristic> getCharacteristics() {
        return characteristics;
    }

    public void setCharacteristics(Map<String, UserCharacteristic> characteristics) {
        this.characteristics = characteristics;
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

package com.game.bunker.lobby.entity;

import java.util.HashSet;
import java.util.Set;


public class Lobby {
    private String id;
    private LobbyStatus status;
    private String adminId;
    private Set<String> userIds = new HashSet<>();

    public Lobby() {
    }

    public Lobby(String id, LobbyStatus status, String adminId, Set<String> userIds) {
        this.id = id;
        this.status = status;
        this.adminId = adminId;
        this.userIds = userIds;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LobbyStatus getStatus() {
        return status;
    }

    public void setStatus(LobbyStatus status) {
        this.status = status;
    }

    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    public Set<String> getUserIds() {
        return userIds;
    }

    public void setUserIds(Set<String> userIds) {
        this.userIds = userIds;
    }

    //helper methods
    public void addUser(String userId){
        this.userIds.add(userId);
    }

    public void removeUser(String userId){
        this.userIds.remove(userId);
    }
}

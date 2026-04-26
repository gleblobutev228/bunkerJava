package com.game.bunker.dto;

public record LobbyMessage(
        String sender,
        String content,
        MessageType type
) {
    public enum MessageType {JOIN, LEAVE, CHAT, OPEN, READY}
}


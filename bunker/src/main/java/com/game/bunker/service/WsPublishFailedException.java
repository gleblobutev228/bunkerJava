package com.game.bunker.service;

public class WsPublishFailedException extends RuntimeException {
    public WsPublishFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

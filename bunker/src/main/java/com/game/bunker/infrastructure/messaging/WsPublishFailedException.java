package com.game.bunker.infrastructure.messaging;

public class WsPublishFailedException extends RuntimeException {
    public WsPublishFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}

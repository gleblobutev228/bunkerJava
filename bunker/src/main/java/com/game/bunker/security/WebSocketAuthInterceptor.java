package com.game.bunker.security;

import com.game.bunker.service.UserService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtProvider jwtProvider;
    private final UserService userService;

    public WebSocketAuthInterceptor(JwtProvider jwtProvider, UserService userService) {
        this.jwtProvider = jwtProvider;
        this.userService = userService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String token = resolveBearerToken(accessor);
        if (token == null || !jwtProvider.isValid(token)) {
            throw new IllegalArgumentException("JWT token is missing or invalid");
        }

        String userId = jwtProvider.getUserId(token);
        if (!userService.exists(userId)) {
            throw new IllegalArgumentException("User session has expired");
        }

        accessor.setUser(new StompPrincipal(userId));
        return message;
    }

    private String resolveBearerToken(StompHeaderAccessor accessor) {
        String authorizationHeader = accessor.getFirstNativeHeader(AUTHORIZATION_HEADER);
        if (authorizationHeader == null || !authorizationHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }
        return authorizationHeader.substring(BEARER_PREFIX.length());
    }
}

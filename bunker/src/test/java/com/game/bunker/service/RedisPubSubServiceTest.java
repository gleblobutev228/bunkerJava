package com.game.bunker.service;

import com.game.bunker.dto.ws.LobbyChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisPubSubServiceTest {
    private StringRedisTemplate redisTemplate;
    private SimpMessagingTemplate messagingTemplate;
    private ObjectMapper objectMapper;
    private RedisPubSubService redisPubSubService;

    @BeforeEach
    void setUp() throws Exception {
        redisTemplate = mock(StringRedisTemplate.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        redisPubSubService = new RedisPubSubService(redisTemplate, messagingTemplate, objectMapper);
    }

    @Test
    void publishCriticalBroadcastWithRetry_throwsAfterMaxAttempts() {
        doThrow(new RuntimeException("redis unavailable"))
                .when(redisTemplate)
                .convertAndSend(any(), any());

        assertThrows(
                WsPublishFailedException.class,
                () -> redisPubSubService.publishCriticalBroadcastWithRetry(
                        "/topic/lobby/ROOM1",
                        RedisPubSubService.RedisWsEventType.LOBBY_STATUS_CHANGED,
                        new LobbyChatMessage("ROOM1", "u1", "nick", "msg", Instant.now())
                )
        );

        verify(redisTemplate, times(3)).convertAndSend(any(), any());
    }

    @Test
    void publishChatBestEffort_doesNotThrowOnRedisFailure() {
        doThrow(new RuntimeException("redis unavailable"))
                .when(redisTemplate)
                .convertAndSend(any(), any());

        assertDoesNotThrow(() -> redisPubSubService.publishChatBestEffort(
                "/topic/lobby/ROOM1",
                RedisPubSubService.RedisWsEventType.LOBBY_CHAT,
                new LobbyChatMessage("ROOM1", "u1", "nick", "msg", Instant.now())
        ));
    }
}

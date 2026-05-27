package com.game.bunker.repository;

import com.game.bunker.dto.ws.LobbyChatMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LobbyRepositoryTest {
    private StringRedisTemplate redisTemplate;
    private ObjectMapper objectMapper;
    private LobbyRepository lobbyRepository;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        objectMapper = mock(ObjectMapper.class);
        lobbyRepository = new LobbyRepository(redisTemplate, objectMapper);
    }

    @Test
    void addChatMessage_usesRedisListAndTrim() throws Exception {
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = (ListOperations<String, String>) mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(redisTemplate.getExpire("lobby:ROOM1")).thenReturn(60L);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"m\":\"hello\"}");

        LobbyChatMessage message = new LobbyChatMessage("ROOM1", "u1", "nick", "hello", Instant.now());
        lobbyRepository.addChatMessage("ROOM1", message);

        verify(listOperations).leftPush("lobby:ROOM1:chat", "{\"m\":\"hello\"}");
        verify(listOperations).trim("lobby:ROOM1:chat", 0, 49);
        verify(redisTemplate).expire(eq("lobby:ROOM1:chat"), any());
    }

    @Test
    void getChatHistory_returnsChronologicalOrder() throws Exception {
        @SuppressWarnings("unchecked")
        ListOperations<String, String> listOperations = (ListOperations<String, String>) mock(ListOperations.class);
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        when(listOperations.range("lobby:ROOM1:chat", 0, 49)).thenReturn(List.of("newer", "older"));

        LobbyChatMessage older = new LobbyChatMessage("ROOM1", "u1", "nick1", "first", Instant.now());
        LobbyChatMessage newer = new LobbyChatMessage("ROOM1", "u2", "nick2", "second", Instant.now());
        when(objectMapper.readValue("older", LobbyChatMessage.class)).thenReturn(older);
        when(objectMapper.readValue("newer", LobbyChatMessage.class)).thenReturn(newer);

        List<LobbyChatMessage> history = lobbyRepository.getChatHistory("ROOM1");

        assertEquals(2, history.size());
        assertEquals("first", history.get(0).message());
        assertEquals("second", history.get(1).message());
    }
}

package com.game.bunker.controller;

import com.game.bunker.dto.LobbyMessage;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WSLobbyHandler {
    private final SimpMessagingTemplate MessagingTemplate;

    @MessageMapping("/topic/lobby.{code}.join")
    public void joinLobby(@DestinationVariable String code,
                          @Payload @Valid LobbyMessage message,
                          SimpMessageHeaderAccessor headerAccessor){}
}

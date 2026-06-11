package com.game.bunker.service;

import com.game.bunker.dto.ws.AdminGameActionMessage;
import com.game.bunker.dto.ws.ClientSessionCommandMessage;
import com.game.bunker.dto.ws.GameActionMessage;
import com.game.bunker.dto.ws.GameStartedMessage;
import com.game.bunker.dto.ws.LobbyChatMessage;
import com.game.bunker.dto.ws.LobbyStateMessage;
import com.game.bunker.dto.ws.LobbyStatusMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

@Service
public class RedisPubSubService implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(RedisPubSubService.class);
    public static final String WS_CHANNEL = "bunker:ws:messages";
    private static final int MAX_CRITICAL_PUBLISH_ATTEMPTS = 3;
    private static final long[] RETRY_DELAYS_MS = {50L, 150L, 300L};

    private final StringRedisTemplate redisTemplate;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final LongAdder publishSuccessCounter = new LongAdder();
    private final LongAdder publishFailureCounter = new LongAdder();
    private final LongAdder publishRetryCounter = new LongAdder();

    public RedisPubSubService(StringRedisTemplate redisTemplate,
                              SimpMessagingTemplate messagingTemplate,
                              ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishChatBestEffort(String destination, RedisWsEventType eventType, Object payload) {
        String messageId = UUID.randomUUID().toString();
        try {
            RedisWsEnvelope envelope = createEnvelope(
                    RedisWsDeliveryType.BROADCAST,
                    destination,
                    null,
                    eventType,
                    payload,
                    messageId
            );
            redisTemplate.convertAndSend(WS_CHANNEL, objectMapper.writeValueAsString(envelope));
            publishSuccessCounter.increment();
            log.info("redis_ws_publish status=success mode=best_effort type={} destination={} messageId={}",
                    eventType, destination, messageId);
        } catch (Exception e) {
            publishFailureCounter.increment();
            log.warn("redis_ws_publish status=failed mode=best_effort type={} destination={} messageId={} reason={}",
                    eventType, destination, messageId, e.getMessage());
        }
    }

    public void publishCriticalBroadcastWithRetry(String destination, RedisWsEventType eventType, Object payload) {
        publishCriticalWithRetry(RedisWsDeliveryType.BROADCAST, destination, null, eventType, payload);
    }

    public void publishCriticalToUserWithRetry(String userId, String destination, RedisWsEventType eventType, Object payload) {
        publishCriticalWithRetry(RedisWsDeliveryType.USER, destination, userId, eventType, payload);
    }

    // TODO(senior): Блокирующий retry через Thread.sleep держит поток вызывающего сервиса; вынести в RetryTemplate/очередь с backoff.
    private void publishCriticalWithRetry(RedisWsDeliveryType deliveryType,
                                          String destination,
                                          String userId,
                                          RedisWsEventType eventType,
                                          Object payload) {
        String messageId = UUID.randomUUID().toString();
        Exception lastException = null;
        for (int attempt = 1; attempt <= MAX_CRITICAL_PUBLISH_ATTEMPTS; attempt++) {
            try {
                RedisWsEnvelope envelope = createEnvelope(
                        deliveryType,
                        destination,
                        userId,
                        eventType,
                        payload,
                        messageId
                );
                redisTemplate.convertAndSend(WS_CHANNEL, objectMapper.writeValueAsString(envelope));
                publishSuccessCounter.increment();
                log.info("redis_ws_publish status=success mode=critical type={} destination={} userId={} attempt={} messageId={}",
                        eventType, destination, userId, attempt, messageId);
                return;
            } catch (Exception e) {
                lastException = e;
                publishFailureCounter.increment();
                if (attempt < MAX_CRITICAL_PUBLISH_ATTEMPTS) {
                    publishRetryCounter.increment();
                    log.warn("redis_ws_publish status=retry type={} destination={} userId={} attempt={} messageId={} reason={}",
                            eventType, destination, userId, attempt, messageId, e.getMessage());
                    sleepBeforeRetry(attempt);
                    continue;
                }
                log.error("redis_ws_publish status=failed mode=critical type={} destination={} userId={} attempt={} messageId={}",
                        eventType, destination, userId, attempt, messageId, e);
            }
        }
        throw new WsPublishFailedException("Failed to publish critical WebSocket event to Redis", lastException);
    }

    private RedisWsEnvelope createEnvelope(RedisWsDeliveryType deliveryType,
                                           String destination,
                                           String userId,
                                           RedisWsEventType eventType,
                                           Object payload,
                                           String messageId) throws Exception {
        return new RedisWsEnvelope(
                messageId,
                Instant.now().toString(),
                deliveryType,
                eventType,
                destination,
                userId,
                objectMapper.writeValueAsString(payload)
        );
    }

    // TODO(senior): Нарушение масштабируемости: sleep в сервисе блокирует рабочий поток, лучше использовать неблокирующий retry или scheduler.
    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_DELAYS_MS[Math.max(0, attempt - 1)]);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new WsPublishFailedException("Interrupted while retrying critical Redis publish", interruptedException);
        }
    }

    // TODO(senior): Redis Pub/Sub без подтверждений, дедупликации и очереди ошибок опасен для критичных игровых событий; добавить идемпотентность или надежный брокер.
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String body = redisTemplate.getStringSerializer().deserialize(message.getBody());
            if (body == null || body.isBlank()) {
                log.warn("redis_ws_consume status=ignored reason=empty_payload");
                return;
            }

            RedisWsEnvelope envelope = objectMapper.readValue(body, RedisWsEnvelope.class);
            Object payload = deserializePayload(envelope.eventType(), envelope.payloadJson());
            if (envelope.deliveryType() == RedisWsDeliveryType.BROADCAST) {
                messagingTemplate.convertAndSend(envelope.destination(), payload);
            } else if (envelope.deliveryType() == RedisWsDeliveryType.USER) {
                if (envelope.userId() == null || envelope.userId().isBlank()) {
                    log.warn("redis_ws_consume status=ignored reason=missing_user_id type={} destination={} messageId={}",
                            envelope.eventType(), envelope.destination(), envelope.messageId());
                    return;
                }
                messagingTemplate.convertAndSendToUser(envelope.userId(), envelope.destination(), payload);
            } else {
                log.warn("redis_ws_consume status=ignored reason=unknown_delivery_type type={} messageId={}",
                        envelope.eventType(), envelope.messageId());
                return;
            }
            log.info("redis_ws_consume status=success type={} destination={} userId={} messageId={}",
                    envelope.eventType(), envelope.destination(), envelope.userId(), envelope.messageId());
        } catch (Exception e) {
            log.error("redis_ws_consume status=failed reason={}", e.getMessage(), e);
        }
    }

    // TODO(senior): Нарушение OCP: при добавлении нового события нужно менять switch; вынести маппинг типов payload в реестр/стратегии.
    private Object deserializePayload(RedisWsEventType eventType, String payloadJson) throws Exception {
        return switch (eventType) {
            case LOBBY_CHAT -> objectMapper.readValue(payloadJson, LobbyChatMessage.class);
            case LOBBY_STATUS_CHANGED -> objectMapper.readValue(payloadJson, LobbyStatusMessage.class);
            case GAME_STARTED -> objectMapper.readValue(payloadJson, GameStartedMessage.class);
            case GAME_ACTION -> objectMapper.readValue(payloadJson, GameActionMessage.class);
            case ADMIN_GAME_ACTION -> objectMapper.readValue(payloadJson, AdminGameActionMessage.class);
            case CLEAR_JWT -> objectMapper.readValue(payloadJson, ClientSessionCommandMessage.class);
            case LOBBY_STATE -> objectMapper.readValue(payloadJson, LobbyStateMessage.class);
        };
    }

    public long publishSuccessCount() {
        return publishSuccessCounter.sum();
    }

    public long publishFailureCount() {
        return publishFailureCounter.sum();
    }

    public long publishRetryCount() {
        return publishRetryCounter.sum();
    }

    public enum RedisWsDeliveryType {
        BROADCAST,
        USER
    }

    public enum RedisWsEventType {
        LOBBY_CHAT,
        LOBBY_STATUS_CHANGED,
        GAME_STARTED,
        GAME_ACTION,
        ADMIN_GAME_ACTION,
        CLEAR_JWT,
        LOBBY_STATE
    }

    public record RedisWsEnvelope(
            String messageId,
            String createdAt,
            RedisWsDeliveryType deliveryType,
            RedisWsEventType eventType,
            String destination,
            String userId,
            String payloadJson
    ) {
    }
}

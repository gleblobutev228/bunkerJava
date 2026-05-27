package com.game.bunker.config;

import com.game.bunker.service.RedisPubSubService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisConfig {
    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    @Bean
    @ConditionalOnProperty(name = "app.ws.redis.enabled", havingValue = "true", matchIfMissing = true)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RedisPubSubService redisPubSubService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(redisPubSubService, new ChannelTopic(RedisPubSubService.WS_CHANNEL));
        container.setErrorHandler(error -> log.error("Redis message listener error", error));
        return container;
    }
}

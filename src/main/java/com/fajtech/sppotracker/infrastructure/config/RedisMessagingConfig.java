package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.infrastructure.adapter.in.websocket.VehiclePositionEventRelay;
import com.fajtech.sppotracker.infrastructure.adapter.out.messaging.RedisVehiclePositionEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.ChannelTopic;

/**
 * Assina o relay ({@link VehiclePositionEventRelay}) ao canal Pub/Sub dos eventos
 * de posição (docs/regras-de-negocio.md §7.4), fechando o caminho
 * Redis Pub/Sub → STOMP.
 */
@Configuration
public class RedisMessagingConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory, VehiclePositionEventRelay relay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relay, new ChannelTopic(RedisVehiclePositionEventPublisher.CHANNEL));
        return container;
    }
}

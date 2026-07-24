package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.infrastructure.adapter.in.messaging.RouteDeviationPositionSubscriber;
import com.fajtech.sppotracker.infrastructure.adapter.in.websocket.RouteDeviationEventRelay;
import com.fajtech.sppotracker.infrastructure.adapter.in.websocket.VehiclePositionEventRelay;
import com.fajtech.sppotracker.infrastructure.adapter.out.messaging.RedisRouteDeviationEventPublisher;
import com.fajtech.sppotracker.infrastructure.adapter.out.messaging.RedisVehiclePositionEventPublisher;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Assina os relays/subscribers aos canais Pub/Sub (docs/regras-de-negocio.md §7.4):
 * o relay de posições ({@link VehiclePositionEventRelay}) e, quando a detecção de
 * desvio está ativa, o subscriber que a alimenta ({@link RouteDeviationPositionSubscriber},
 * no canal de posições) e o relay de desvios ({@link RouteDeviationEventRelay}, no canal
 * de desvios → STOMP).
 */
@Configuration
public class RedisMessagingConfig {

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            VehiclePositionEventRelay relay,
            ObjectProvider<RouteDeviationPositionSubscriber> deviationSubscriber,
            ObjectProvider<RouteDeviationEventRelay> deviationRelay) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        ChannelTopic positionsChannel = new ChannelTopic(RedisVehiclePositionEventPublisher.CHANNEL);
        container.addMessageListener(relay, positionsChannel);
        deviationSubscriber.ifAvailable(subscriber -> container.addMessageListener(subscriber, positionsChannel));
        deviationRelay.ifAvailable(devRelay ->
                container.addMessageListener(devRelay, new ChannelTopic(RedisRouteDeviationEventPublisher.CHANNEL)));

        return container;
    }
}

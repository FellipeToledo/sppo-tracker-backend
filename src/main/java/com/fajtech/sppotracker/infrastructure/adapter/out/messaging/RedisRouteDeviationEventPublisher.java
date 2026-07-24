package com.fajtech.sppotracker.infrastructure.adapter.out.messaging;

import com.fajtech.sppotracker.application.port.out.PublishRouteDeviationEventPort;
import com.fajtech.sppotracker.domain.route.RouteDeviationEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Publica o evento de desvio no canal Redis Pub/Sub (docs/regras-de-negocio.md §5.5,
 * §7.2), de onde o relay o repassa para {@code /topic/route-deviations}. Registrado como
 * bean condicional (só com a detecção ativa).
 */
public class RedisRouteDeviationEventPublisher implements PublishRouteDeviationEventPort {

    /** Canal Pub/Sub dos eventos de desvio. */
    public static final String CHANNEL = "gps:events:route-deviations";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisRouteDeviationEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publish(RouteDeviationEvent event) {
        String json;
        try {
            json = objectMapper.writeValueAsString(RouteDeviationMessage.from(event));
        } catch (JacksonException e) {
            throw new IllegalStateException("Falha ao serializar evento de desvio do veículo "
                    + event.vehicleId(), e);
        }
        redisTemplate.convertAndSend(CHANNEL, json);
    }
}

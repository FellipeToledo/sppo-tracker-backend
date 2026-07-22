package com.fajtech.sppotracker.infrastructure.adapter.out.messaging;

import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.application.port.out.PublishVehiclePositionEventPort;
import com.fajtech.sppotracker.domain.operator.Operator;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.VehiclePositionResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica o evento de posição no canal Redis Pub/Sub (docs/regras-de-negocio.md
 * §7.2, §7.4). O payload é o mesmo {@link VehiclePositionResponse} do REST, para
 * que os clientes WebSocket recebam o formato idêntico ao {@code /current}.
 */
@Component
public class RedisVehiclePositionEventPublisher implements PublishVehiclePositionEventPort {

    /** Canal Pub/Sub dos eventos de posição. */
    public static final String CHANNEL = "gps:events:vehicle-positions";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OperatorQueryUseCase operators;

    public RedisVehiclePositionEventPublisher(StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
                                              OperatorQueryUseCase operators) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.operators = operators;
    }

    @Override
    public void publish(ClassifiedVehiclePosition event) {
        String operatorName = operators.resolve(event.position().vehicleId())
                .map(Operator::name)
                .orElse(null);
        String json;
        try {
            json = objectMapper.writeValueAsString(VehiclePositionResponse.from(event, operatorName));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar evento de posição do veículo "
                    + event.position().vehicleId(), e);
        }
        redisTemplate.convertAndSend(CHANNEL, json);
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.out.messaging;

import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.domain.operator.Operator;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.VehiclePositionResponse;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Publicação do evento de posição no canal Redis (§7.2, §7.4). */
class RedisVehiclePositionEventPublisherTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private StringRedisTemplate redisTemplate;
    private OperatorQueryUseCase operators;
    private RedisVehiclePositionEventPublisher publisher;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        operators = mock(OperatorQueryUseCase.class);
        publisher = new RedisVehiclePositionEventPublisher(redisTemplate, objectMapper, operators);
    }

    private static ClassifiedVehiclePosition event() {
        VehiclePosition position = VehiclePosition.builder()
                .vehicleId("A12345")
                .serviceCode("100")
                .coordinates(new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2")))
                .speed(30.0)
                .positionTimestamp(Instant.parse("2026-07-22T12:00:00Z"))
                .sentTimestamp(Instant.parse("2026-07-22T12:00:02Z"))
                .receivedAt(Instant.parse("2026-07-22T12:00:03Z"))
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
        return new ClassifiedVehiclePosition(position,
                PositionClassification.from(VehiclePositionStatus.IN_OPERATION, Set.of()));
    }

    @Test
    void shouldPublishResponseJsonWithOperatorToChannel() throws Exception {
        ClassifiedVehiclePosition event = event();
        when(operators.resolve("A12345")).thenReturn(Optional.of(new Operator("A123", "Empresa X")));
        String expectedJson = objectMapper.writeValueAsString(VehiclePositionResponse.from(event, "Empresa X"));

        publisher.publish(event);

        verify(redisTemplate).convertAndSend(
                RedisVehiclePositionEventPublisher.CHANNEL, expectedJson);
    }
}

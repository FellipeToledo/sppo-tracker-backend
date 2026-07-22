package com.fajtech.sppotracker.infrastructure.adapter.in.websocket;

import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.VehiclePositionResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Relay Redis Pub/Sub → tópicos STOMP (§7.2). */
class VehiclePositionEventRelayTest {

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private SimpMessagingTemplate messagingTemplate;
    private VehiclePositionEventRelay relay;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        relay = new VehiclePositionEventRelay(messagingTemplate, objectMapper);
    }

    private static Message messageWith(String json) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(json.getBytes(StandardCharsets.UTF_8));
        return message;
    }

    private VehiclePositionResponse response(String serviceCode, String routeId) {
        return new VehiclePositionResponse(
                "A12345", serviceCode, null, routeId, null, null,
                new BigDecimal("-22.9"), new BigDecimal("-43.2"), 30.0, null,
                Instant.parse("2026-07-22T12:00:00Z"), Instant.parse("2026-07-22T12:00:02Z"),
                null, Instant.parse("2026-07-22T12:00:03Z"), "DADOS_MOBILIDADE_RIO",
                com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus.IN_OPERATION,
                true, true, false, true, List.of(), "Empresa X");
    }

    @Test
    void shouldFanOutToGlobalServiceAndRouteTopics() throws Exception {
        VehiclePositionResponse dto = response("100", "R1");
        relay.onMessage(messageWith(objectMapper.writeValueAsString(dto)), null);

        verify(messagingTemplate).convertAndSend("/topic/vehicle-positions", dto);
        verify(messagingTemplate).convertAndSend("/topic/vehicle-positions/service/100", dto);
        verify(messagingTemplate).convertAndSend("/topic/vehicle-positions/route/R1", dto);
    }

    @Test
    void shouldOnlySendToGlobalTopicWhenServiceAndRouteAbsent() throws Exception {
        VehiclePositionResponse dto = response(null, null);
        relay.onMessage(messageWith(objectMapper.writeValueAsString(dto)), null);

        verify(messagingTemplate).convertAndSend("/topic/vehicle-positions", dto);
        verify(messagingTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.startsWith("/topic/vehicle-positions/service/"),
                org.mockito.ArgumentMatchers.any(Object.class));
        verify(messagingTemplate, never()).convertAndSend(
                org.mockito.ArgumentMatchers.startsWith("/topic/vehicle-positions/route/"),
                org.mockito.ArgumentMatchers.any(Object.class));
    }
}

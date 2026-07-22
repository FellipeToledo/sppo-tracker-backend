package com.fajtech.sppotracker.infrastructure.adapter.in.websocket;

import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.VehiclePositionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Relay do barramento Redis Pub/Sub para os tópicos STOMP (docs/regras-de-negocio.md
 * §7.2). Recebe o evento de posição publicado no canal e o repassa para o tópico
 * global e, quando presentes, para os tópicos por serviço e por rota.
 */
@Component
public class VehiclePositionEventRelay implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(VehiclePositionEventRelay.class);

    private static final String TOPIC = "/topic/vehicle-positions";
    private static final String TOPIC_BY_SERVICE = TOPIC + "/service/";
    private static final String TOPIC_BY_ROUTE = TOPIC + "/route/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public VehiclePositionEventRelay(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        VehiclePositionResponse event;
        try {
            event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), VehiclePositionResponse.class);
        } catch (JacksonException e) {
            log.warn("Descartando evento de posição malformado no canal Pub/Sub", e);
            return;
        }
        messagingTemplate.convertAndSend(TOPIC, event);
        if (event.serviceCode() != null) {
            messagingTemplate.convertAndSend(TOPIC_BY_SERVICE + event.serviceCode(), event);
        }
        if (event.routeId() != null) {
            messagingTemplate.convertAndSend(TOPIC_BY_ROUTE + event.routeId(), event);
        }
    }
}

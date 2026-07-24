package com.fajtech.sppotracker.infrastructure.adapter.in.websocket;

import com.fajtech.sppotracker.infrastructure.adapter.out.messaging.RouteDeviationMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Relay do canal Redis Pub/Sub de desvios para os tópicos STOMP
 * (docs/regras-de-negocio.md §5.5, §7.2): repassa para o tópico global e, quando há
 * rota, para {@code /topic/route-deviations/route/{routeId}}.
 */
public class RouteDeviationEventRelay implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RouteDeviationEventRelay.class);

    private static final String TOPIC = "/topic/route-deviations";
    private static final String TOPIC_BY_ROUTE = TOPIC + "/route/";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public RouteDeviationEventRelay(SimpMessagingTemplate messagingTemplate, ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        RouteDeviationMessage event;
        try {
            event = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), RouteDeviationMessage.class);
        } catch (JacksonException e) {
            log.warn("Descartando evento de desvio malformado no canal Pub/Sub", e);
            return;
        }
        messagingTemplate.convertAndSend(TOPIC, event);
        if (event.routeId() != null) {
            messagingTemplate.convertAndSend(TOPIC_BY_ROUTE + event.routeId(), event);
        }
    }
}

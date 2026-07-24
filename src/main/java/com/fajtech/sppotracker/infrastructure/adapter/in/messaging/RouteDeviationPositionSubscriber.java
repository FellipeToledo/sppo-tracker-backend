package com.fajtech.sppotracker.infrastructure.adapter.in.messaging;

import com.fajtech.sppotracker.application.route.RouteDeviationService;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.VehiclePositionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/**
 * Consome o stream de posições publicado (canal Redis Pub/Sub, mesmo formato do REST) e
 * alimenta a detecção de desvio (docs/regras-de-negocio.md §5) — <b>fora</b> do hot path
 * de polling, mantendo a máquina de desvio ortogonal à classificação. Reconstrói a
 * {@link VehiclePosition} a partir do payload e delega ao {@link RouteDeviationService}.
 */
public class RouteDeviationPositionSubscriber implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(RouteDeviationPositionSubscriber.class);

    private final RouteDeviationService service;
    private final ObjectMapper objectMapper;

    public RouteDeviationPositionSubscriber(RouteDeviationService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        VehiclePositionResponse dto;
        try {
            dto = objectMapper.readValue(
                    new String(message.getBody(), StandardCharsets.UTF_8), VehiclePositionResponse.class);
        } catch (JacksonException e) {
            log.warn("Descartando posição malformada no canal Pub/Sub (desvio)", e);
            return;
        }
        VehiclePosition position = toPosition(dto);
        if (position != null) {
            service.onPosition(position);
        }
    }

    private static VehiclePosition toPosition(VehiclePositionResponse dto) {
        if (dto.latitude() == null || dto.longitude() == null) {
            return null;
        }
        try {
            return VehiclePosition.builder()
                    .vehicleId(dto.vehicleId())
                    .serviceCode(dto.serviceCode())
                    .directionCode(dto.directionCode())
                    .routeId(dto.routeId())
                    .tripId(dto.tripId())
                    .shapeId(dto.shapeId())
                    .coordinates(new Coordinates(dto.latitude(), dto.longitude()))
                    .speed(dto.speed())
                    .heading(dto.heading())
                    .positionTimestamp(dto.positionTimestamp())
                    .sentTimestamp(dto.sentTimestamp())
                    .serverTimestamp(dto.serverTimestamp())
                    .receivedAt(dto.receivedAt())
                    .source(parseSource(dto.source()))
                    .build();
        } catch (RuntimeException invalid) {
            log.debug("Ignorando posição não reconstruível para desvio: {}", invalid.toString());
            return null;
        }
    }

    private static PositionSource parseSource(String source) {
        try {
            return source == null ? PositionSource.DADOS_MOBILIDADE_RIO : PositionSource.valueOf(source);
        } catch (IllegalArgumentException unknown) {
            return PositionSource.DADOS_MOBILIDADE_RIO;
        }
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.out.messaging;

import com.fajtech.sppotracker.domain.route.RouteDeviationEvent;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Payload de um evento de desvio no barramento (Redis Pub/Sub) e no WebSocket
 * (docs/regras-de-negocio.md §5.5, §7.2). Formato estável consumido pelo dashboard em
 * {@code /topic/route-deviations}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RouteDeviationMessage(
        String vehicleId,
        String serviceCode,
        String routeId,
        String type,
        String severity,
        double distanceMeters,
        Instant occurredAt) {

    public static RouteDeviationMessage from(RouteDeviationEvent event) {
        return new RouteDeviationMessage(
                event.vehicleId(),
                event.serviceCode(),
                event.routeId(),
                event.type().name(),
                event.severity() == null ? null : event.severity().name(),
                event.distanceMeters(),
                event.occurredAt());
    }
}

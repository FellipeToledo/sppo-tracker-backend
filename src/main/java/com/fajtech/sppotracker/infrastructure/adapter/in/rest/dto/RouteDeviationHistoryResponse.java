package com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppotracker.application.route.RouteDeviationHistoryEntry;

import java.time.Instant;

/**
 * Representação REST de um evento de desvio no histórico (docs/regras-de-negocio.md §7.1).
 * {@code type}/{@code severity} expostos pelo nome do enum ({@code severity} pode ser nulo).
 */
public record RouteDeviationHistoryResponse(
        Long id,
        String vehicleId,
        String serviceCode,
        String routeId,
        String type,
        String severity,
        Double distanceMeters,
        Instant occurredAt) {

    public static RouteDeviationHistoryResponse from(RouteDeviationHistoryEntry entry) {
        return new RouteDeviationHistoryResponse(
                entry.id(),
                entry.vehicleId(),
                entry.serviceCode(),
                entry.routeId(),
                entry.type() == null ? null : entry.type().name(),
                entry.severity() == null ? null : entry.severity().name(),
                entry.distanceMeters(),
                entry.occurredAt());
    }
}

package com.fajtech.sppotracker.application.route;

import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.DeviationSeverity;

import java.time.Instant;

/**
 * Registro de leitura do histórico de desvios (docs/regras-de-negocio.md §7.1, §7.4).
 * {@code severity} e {@code distanceMeters} podem ser nulos (ex.: eventos sem distância).
 */
public record RouteDeviationHistoryEntry(
        Long id,
        String vehicleId,
        String serviceCode,
        String routeId,
        DeviationEventType type,
        DeviationSeverity severity,
        Double distanceMeters,
        Instant occurredAt) {
}

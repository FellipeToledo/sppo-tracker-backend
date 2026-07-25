package com.fajtech.sppotracker.application.route;

import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.DeviationSeverity;

/**
 * Filtro de consulta do histórico de desvios (docs/regras-de-negocio.md §5.5, §7.1).
 * Campos nulos = sem filtro. {@code limit} é normalizado para [1, {@value #MAX_LIMIT}].
 */
public record RouteDeviationQuery(
        String vehicleId,
        String serviceCode,
        DeviationEventType type,
        DeviationSeverity severity,
        int limit) {

    public static final int DEFAULT_LIMIT = 100;
    public static final int MAX_LIMIT = 500;

    public RouteDeviationQuery {
        vehicleId = blankToNull(vehicleId);
        serviceCode = blankToNull(serviceCode);
        if (limit <= 0) {
            limit = DEFAULT_LIMIT;
        } else if (limit > MAX_LIMIT) {
            limit = MAX_LIMIT;
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

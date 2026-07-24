package com.fajtech.sppotracker.application.port.in;

import com.fajtech.sppotracker.application.route.RouteDeviationHistoryEntry;
import com.fajtech.sppotracker.application.route.RouteDeviationQuery;

import java.util.List;

/**
 * Caso de uso de consulta do histórico de eventos de desvio
 * (docs/regras-de-negocio.md §5.5, §7.1).
 */
public interface GetRouteDeviationHistoryUseCase {

    List<RouteDeviationHistoryEntry> recent(RouteDeviationQuery query);
}

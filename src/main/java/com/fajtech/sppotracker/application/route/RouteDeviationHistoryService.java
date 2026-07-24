package com.fajtech.sppotracker.application.route;

import com.fajtech.sppotracker.application.port.in.GetRouteDeviationHistoryUseCase;
import com.fajtech.sppotracker.application.port.out.RouteDeviationHistoryPort;

import java.util.List;
import java.util.Objects;

/**
 * Consulta o histórico de desvios via {@link RouteDeviationHistoryPort}
 * (docs/regras-de-negocio.md §7.1). Classe pura de aplicação, instanciada pela infra.
 */
public class RouteDeviationHistoryService implements GetRouteDeviationHistoryUseCase {

    private final RouteDeviationHistoryPort historyPort;

    public RouteDeviationHistoryService(RouteDeviationHistoryPort historyPort) {
        this.historyPort = Objects.requireNonNull(historyPort, "historyPort");
    }

    @Override
    public List<RouteDeviationHistoryEntry> recent(RouteDeviationQuery query) {
        return historyPort.findRecent(query == null
                ? new RouteDeviationQuery(null, null, null, null, RouteDeviationQuery.DEFAULT_LIMIT)
                : query);
    }
}

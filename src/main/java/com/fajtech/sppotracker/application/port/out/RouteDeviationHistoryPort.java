package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.application.route.RouteDeviationHistoryEntry;
import com.fajtech.sppotracker.application.route.RouteDeviationQuery;

import java.util.List;

/**
 * Porta de saída para ler o histórico de desvios persistido
 * (docs/regras-de-negocio.md §7.4). Resultados por {@code occurredAt} decrescente.
 */
public interface RouteDeviationHistoryPort {

    List<RouteDeviationHistoryEntry> findRecent(RouteDeviationQuery query);
}

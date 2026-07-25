package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.route.RouteDeviationEvent;

/**
 * Porta de saída para persistir um evento de desvio no histórico
 * (docs/regras-de-negocio.md §5.5, §7.4).
 */
public interface RecordRouteDeviationEventPort {

    void record(RouteDeviationEvent event);
}

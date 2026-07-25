package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.route.RouteDeviationEvent;

/**
 * Porta de saída para publicar um evento de desvio em tempo real
 * (docs/regras-de-negocio.md §5.5, §7.2 → {@code /topic/route-deviations}).
 */
public interface PublishRouteDeviationEventPort {

    void publish(RouteDeviationEvent event);
}

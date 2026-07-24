package com.fajtech.sppotracker.infrastructure.adapter.in.scheduler;

import com.fajtech.sppotracker.application.route.RouteDeviationService;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Dispara o sweep periódico da máquina de desvio (docs/regras-de-negocio.md §5.3):
 * consolida episódios de veículos silenciosos fora do corredor e poda estados
 * expirados. Registrado como bean condicional (só quando a detecção está ativa).
 */
public class RouteDeviationSweepScheduler {

    private final RouteDeviationService service;

    public RouteDeviationSweepScheduler(RouteDeviationService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${gps.route.deviation.sweep-interval:30s}")
    public void sweep() {
        service.sweep();
    }
}

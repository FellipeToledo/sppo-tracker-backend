package com.fajtech.sppotracker.infrastructure.adapter.in.scheduler;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;
import com.fajtech.sppotracker.application.port.in.RunGpsPollingCycleUseCase;
import com.fajtech.sppotracker.infrastructure.observability.PollingMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Adapter de entrada que dispara o ciclo de polling em intervalo fixo
 * (docs/regras-de-negocio.md §3.1). Não contém regra de negócio — apenas o
 * gatilho; toda a orquestração (readiness, cooldown, janela) vive no caso de uso.
 *
 * <p>Desligável via {@code gps.polling.enabled=false} (env {@code GPS_POLLING_ENABLED}).
 */
@Component
@ConditionalOnProperty(prefix = "gps.polling", name = "enabled", havingValue = "true", matchIfMissing = true)
public class GpsPollingScheduler {

    private static final Logger log = LoggerFactory.getLogger(GpsPollingScheduler.class);

    private final RunGpsPollingCycleUseCase useCase;
    private final PollingMetrics pollingMetrics;

    public GpsPollingScheduler(RunGpsPollingCycleUseCase useCase, PollingMetrics pollingMetrics) {
        this.useCase = useCase;
        this.pollingMetrics = pollingMetrics;
    }

    @Scheduled(fixedDelayString = "${gps.polling.fixed-delay}")
    public void poll() {
        PollingCycleResult result = useCase.runCycle();
        pollingMetrics.record(result);
        if (log.isDebugEnabled()) {
            log.debug("Ciclo de polling: outcome={} received={} consecutiveFailures={} skipReason={}",
                    result.outcome(), result.receivedCount(),
                    result.consecutiveFailures(), result.skipReason());
        }
    }
}

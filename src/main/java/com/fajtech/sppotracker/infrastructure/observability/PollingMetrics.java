package com.fajtech.sppotracker.infrastructure.observability;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;
import com.fajtech.sppotracker.application.port.in.GetGpsPollingStatusUseCase;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

/**
 * Métricas Micrometer do ciclo de polling (docs/regras-de-negocio.md §7.3):
 * contagem por desfecho, duração e o gauge de falhas consecutivas (lido do último
 * status). Alimentado pelo scheduler a cada ciclo.
 */
@Component
public class PollingMetrics {

    private static final String CYCLES = "gps.polling.cycles";

    private final MeterRegistry registry;
    private final Timer cycleDuration;

    public PollingMetrics(MeterRegistry registry, GetGpsPollingStatusUseCase statusUseCase) {
        this.registry = registry;
        this.cycleDuration = Timer.builder("gps.polling.cycle.duration")
                .description("Duração de um ciclo de polling")
                .register(registry);
        registry.gauge("gps.polling.consecutive.failures", statusUseCase,
                s -> s.lastStatus().map(PollingCycleResult::consecutiveFailures).orElse(0));
    }

    public void record(PollingCycleResult result) {
        Counter.builder(CYCLES).tag("outcome", result.outcome().name()).register(registry).increment();
        cycleDuration.record(result.duration());
    }
}

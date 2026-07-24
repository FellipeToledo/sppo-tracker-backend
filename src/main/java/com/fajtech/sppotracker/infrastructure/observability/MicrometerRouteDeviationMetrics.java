package com.fajtech.sppotracker.infrastructure.observability;

import com.fajtech.sppotracker.application.port.out.RouteDeviationMetricsPort;
import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.DeviationSeverity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Métricas Micrometer da máquina de desvio (docs/regras-de-negocio.md §5.5, §7.3):
 * contador {@code gps.route.deviations} por {@code type} e {@code severity}, exposto em
 * {@code /actuator/prometheus}. Só depende do {@link MeterRegistry}, sempre presente.
 */
@Component
public class MicrometerRouteDeviationMetrics implements RouteDeviationMetricsPort {

    private static final String DEVIATIONS = "gps.route.deviations";

    private final MeterRegistry registry;

    public MicrometerRouteDeviationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordEmitted(DeviationEventType type, DeviationSeverity severity) {
        Counter.builder(DEVIATIONS)
                .tag("type", type.name())
                .tag("severity", severity == null ? "NONE" : severity.name())
                .register(registry)
                .increment();
    }
}

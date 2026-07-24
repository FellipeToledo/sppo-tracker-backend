package com.fajtech.sppotracker.infrastructure.observability;

import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.DeviationSeverity;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerRouteDeviationMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerRouteDeviationMetrics metrics = new MicrometerRouteDeviationMetrics(registry);

    @Test
    void shouldCountByTypeAndSeverity() {
        metrics.recordEmitted(DeviationEventType.CONFIRMED, DeviationSeverity.GRAVE);
        metrics.recordEmitted(DeviationEventType.CONFIRMED, DeviationSeverity.GRAVE);
        metrics.recordEmitted(DeviationEventType.ALERT, DeviationSeverity.LEVE);

        assertThat(counter("CONFIRMED", "GRAVE")).isEqualTo(2.0);
        assertThat(counter("ALERT", "LEVE")).isEqualTo(1.0);
    }

    private double counter(String type, String severity) {
        return registry.get("gps.route.deviations")
                .tag("type", type).tag("severity", severity)
                .counter().count();
    }
}

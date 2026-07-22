package com.fajtech.sppotracker.infrastructure.observability;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;
import com.fajtech.sppotracker.application.port.in.GetGpsPollingStatusUseCase;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Métricas de polling sobre um SimpleMeterRegistry (§7.3). */
class PollingMetricsTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    private SimpleMeterRegistry registry;
    private GetGpsPollingStatusUseCase statusUseCase;
    private PollingMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        statusUseCase = mock(GetGpsPollingStatusUseCase.class);
        when(statusUseCase.lastStatus()).thenReturn(Optional.empty());
        metrics = new PollingMetrics(registry, statusUseCase);
    }

    @Test
    void shouldCountCyclesByOutcomeAndTimeDuration() {
        metrics.record(PollingCycleResult.success(
                NOW.minusSeconds(90), NOW, 42, NOW, Duration.ofMillis(120)));
        metrics.record(PollingCycleResult.success(
                NOW.minusSeconds(90), NOW, 10, NOW, Duration.ofMillis(80)));

        assertThat(registry.get("gps.polling.cycles").tag("outcome", "SUCCESS").counter().count())
                .isEqualTo(2.0);
        var timer = registry.get("gps.polling.cycle.duration").timer();
        assertThat(timer.count()).isEqualTo(2L);
        assertThat(timer.totalTime(TimeUnit.MILLISECONDS)).isEqualTo(200.0);
    }

    @Test
    void shouldSeparateOutcomes() {
        metrics.record(PollingCycleResult.failure(NOW.minusSeconds(90), NOW, 1, NOW, Duration.ZERO, "boom"));

        assertThat(registry.get("gps.polling.cycles").tag("outcome", "FAILURE").counter().count())
                .isEqualTo(1.0);
    }

    @Test
    void shouldExposeConsecutiveFailuresGaugeFromLastStatus() {
        when(statusUseCase.lastStatus()).thenReturn(Optional.of(
                PollingCycleResult.failure(NOW.minusSeconds(90), NOW, 3, NOW, Duration.ZERO, "boom")));

        assertThat(registry.get("gps.polling.consecutive.failures").gauge().value()).isEqualTo(3.0);
    }

    @Test
    void shouldGaugeZeroWhenNoCycleYet() {
        assertThat(registry.get("gps.polling.consecutive.failures").gauge().value()).isEqualTo(0.0);
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.in.scheduler;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;
import com.fajtech.sppotracker.application.polling.PollingSkipReason;
import com.fajtech.sppotracker.application.port.in.RunGpsPollingCycleUseCase;
import com.fajtech.sppotracker.infrastructure.observability.PollingMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O scheduler apenas delega ao caso de uso e registra a métrica (adapter fino). */
class GpsPollingSchedulerTest {

    private RunGpsPollingCycleUseCase useCase;
    private PollingMetrics pollingMetrics;
    private GpsPollingScheduler scheduler;

    @BeforeEach
    void setUp() {
        useCase = mock(RunGpsPollingCycleUseCase.class);
        pollingMetrics = mock(PollingMetrics.class);
        scheduler = new GpsPollingScheduler(useCase, pollingMetrics);
    }

    @Test
    void shouldDelegateToUseCaseAndRecordMetric() {
        PollingCycleResult result = PollingCycleResult.success(
                Instant.parse("2026-07-22T11:58:30Z"), Instant.parse("2026-07-22T12:00:00Z"),
                3, Instant.parse("2026-07-22T12:00:00Z"), Duration.ofMillis(5));
        when(useCase.runCycle()).thenReturn(result);

        scheduler.poll();

        verify(useCase).runCycle();
        verify(pollingMetrics).record(result);
    }

    @Test
    void shouldRecordMetricWhenCycleSkipped() {
        PollingCycleResult result = PollingCycleResult.skipped(
                PollingSkipReason.FAILURE_COOLDOWN, 3, Instant.parse("2026-07-22T12:00:00Z"), Duration.ZERO);
        when(useCase.runCycle()).thenReturn(result);

        scheduler.poll();

        verify(pollingMetrics).record(result);
    }
}

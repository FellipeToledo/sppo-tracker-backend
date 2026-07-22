package com.fajtech.sppotracker.infrastructure.adapter.in.scheduler;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;
import com.fajtech.sppotracker.application.polling.PollingOutcome;
import com.fajtech.sppotracker.application.port.in.RunGpsPollingCycleUseCase;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** O scheduler apenas delega ao caso de uso (adapter fino, sem regra). */
class GpsPollingSchedulerTest {

    @Test
    void shouldDelegateToUseCase() {
        RunGpsPollingCycleUseCase useCase = mock(RunGpsPollingCycleUseCase.class);
        when(useCase.runCycle()).thenReturn(PollingCycleResult.success(
                Instant.parse("2026-07-22T11:58:30Z"), Instant.parse("2026-07-22T12:00:00Z"),
                3, Instant.parse("2026-07-22T12:00:00Z"), Duration.ofMillis(5)));

        new GpsPollingScheduler(useCase).poll();

        verify(useCase).runCycle();
    }

    @Test
    void shouldNotThrowWhenCycleSkipped() {
        RunGpsPollingCycleUseCase useCase = mock(RunGpsPollingCycleUseCase.class);
        when(useCase.runCycle()).thenReturn(PollingCycleResult.skipped(
                com.fajtech.sppotracker.application.polling.PollingSkipReason.FAILURE_COOLDOWN,
                3, Instant.parse("2026-07-22T12:00:00Z"), Duration.ZERO));

        new GpsPollingScheduler(useCase).poll();

        verify(useCase).runCycle();
    }

    @Test
    void shouldPropagateOutcomeType() {
        RunGpsPollingCycleUseCase useCase = mock(RunGpsPollingCycleUseCase.class);
        PollingCycleResult result = PollingCycleResult.failure(
                Instant.parse("2026-07-22T11:58:30Z"), Instant.parse("2026-07-22T12:00:00Z"),
                1, Instant.parse("2026-07-22T12:00:00Z"), Duration.ofMillis(2), "boom");
        when(useCase.runCycle()).thenReturn(result);

        new GpsPollingScheduler(useCase).poll();

        org.assertj.core.api.Assertions.assertThat(result.outcome()).isEqualTo(PollingOutcome.FAILURE);
    }
}

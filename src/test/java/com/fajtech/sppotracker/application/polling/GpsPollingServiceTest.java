package com.fajtech.sppotracker.application.polling;

import com.fajtech.sppotracker.application.port.out.FetchExternalGpsPositionsPort;
import com.fajtech.sppotracker.application.port.out.ProviderReadinessPort;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Orquestração do ciclo de polling: readiness, cooldown e janela de sobreposição (§3.1). */
class GpsPollingServiceTest {

    private static final Duration OVERLAP = Duration.ofSeconds(90);
    private static final int THRESHOLD = 3;
    private static final Duration COOLDOWN = Duration.ofMinutes(5);
    private static final Instant T0 = Instant.parse("2026-07-22T12:00:00Z");

    private SettableClock clock;
    private FetchExternalGpsPositionsPort fetchPort;
    private ProviderReadinessPort readinessPort;
    private GpsPollingService service;

    @BeforeEach
    void setUp() {
        clock = new SettableClock(T0);
        fetchPort = mock(FetchExternalGpsPositionsPort.class);
        readinessPort = mock(ProviderReadinessPort.class);
        when(readinessPort.isReady()).thenReturn(true);
        service = new GpsPollingService(fetchPort, readinessPort, clock, OVERLAP, THRESHOLD, COOLDOWN);
    }

    private static VehiclePosition position(String vehicleId) {
        return VehiclePosition.builder()
                .vehicleId(vehicleId)
                .coordinates(new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2")))
                .positionTimestamp(T0)
                .sentTimestamp(T0)
                .receivedAt(T0)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    @Test
    void shouldFetchOverlapWindowAndReturnSuccess() {
        when(fetchPort.fetch(any(), any())).thenReturn(List.of(position("A1"), position("A2")));

        PollingCycleResult result = service.runCycle();

        assertThat(result.outcome()).isEqualTo(PollingOutcome.SUCCESS);
        assertThat(result.receivedCount()).isEqualTo(2);
        assertThat(result.consecutiveFailures()).isZero();
        assertThat(result.windowStart()).isEqualTo(T0.minus(OVERLAP));
        assertThat(result.windowEnd()).isEqualTo(T0);
        assertThat(result.startedAt()).isEqualTo(T0);
        verify(fetchPort).fetch(T0.minus(OVERLAP), T0);
    }

    @Test
    void shouldSkipWhenProviderNotReady() {
        when(readinessPort.isReady()).thenReturn(false);

        PollingCycleResult result = service.runCycle();

        assertThat(result.outcome()).isEqualTo(PollingOutcome.SKIPPED);
        assertThat(result.skipReason()).isEqualTo(PollingSkipReason.PROVIDER_NOT_READY);
        verifyNoInteractions(fetchPort);
    }

    @Test
    void shouldReturnFailureAndIncrementCounterWithoutCooldownBeforeThreshold() {
        when(fetchPort.fetch(any(), any())).thenThrow(new RuntimeException("boom"));

        PollingCycleResult result = service.runCycle();

        assertThat(result.outcome()).isEqualTo(PollingOutcome.FAILURE);
        assertThat(result.consecutiveFailures()).isEqualTo(1);
        assertThat(result.errorMessage()).isEqualTo("boom");

        // segundo ciclo ainda chama o provider (não atingiu o threshold)
        PollingCycleResult second = service.runCycle();
        assertThat(second.outcome()).isEqualTo(PollingOutcome.FAILURE);
        assertThat(second.consecutiveFailures()).isEqualTo(2);
    }

    @Test
    void shouldEnterCooldownAfterThresholdConsecutiveFailures() {
        when(fetchPort.fetch(any(), any())).thenThrow(new RuntimeException("boom"));

        for (int i = 0; i < THRESHOLD; i++) {
            service.runCycle();
        }
        assertThat(service.lastResult().consecutiveFailures()).isEqualTo(THRESHOLD);

        // dentro do cooldown: pula sem chamar o provider
        PollingCycleResult skipped = service.runCycle();
        assertThat(skipped.outcome()).isEqualTo(PollingOutcome.SKIPPED);
        assertThat(skipped.skipReason()).isEqualTo(PollingSkipReason.FAILURE_COOLDOWN);
        // exatamente THRESHOLD chamadas ao provider (a 4ª foi pulada)
        verify(fetchPort, org.mockito.Mockito.times(THRESHOLD)).fetch(any(), any());
    }

    @Test
    void shouldResumePollingAfterCooldownElapses() {
        doThrow(new RuntimeException("boom")).when(fetchPort).fetch(any(), any());
        for (int i = 0; i < THRESHOLD; i++) {
            service.runCycle();
        }

        // avança além do cooldown e faz o provider ter sucesso
        clock.advance(COOLDOWN.plusSeconds(1));
        doReturn(List.of(position("A1"))).when(fetchPort).fetch(any(), any());

        PollingCycleResult result = service.runCycle();

        assertThat(result.outcome()).isEqualTo(PollingOutcome.SUCCESS);
        assertThat(result.consecutiveFailures()).isZero();
    }

    @Test
    void shouldResetFailureCounterOnSuccess() {
        doThrow(new RuntimeException("boom")).when(fetchPort).fetch(any(), any());
        service.runCycle();
        service.runCycle();
        assertThat(service.lastResult().consecutiveFailures()).isEqualTo(2);

        doReturn(List.of()).when(fetchPort).fetch(any(), any());
        assertThat(service.runCycle().consecutiveFailures()).isZero();

        // uma nova falha recomeça do 1
        doThrow(new RuntimeException("again")).when(fetchPort).fetch(any(), any());
        assertThat(service.runCycle().consecutiveFailures()).isEqualTo(1);
    }

    @Test
    void shouldNotCallProviderWhenSkippedAndKeepFailureCount() {
        when(readinessPort.isReady()).thenReturn(false);
        service.runCycle();
        verify(fetchPort, never()).fetch(any(), any());
    }

    /** Clock de teste ajustável para simular a passagem do tempo (cooldown). */
    private static final class SettableClock extends Clock {
        private final AtomicReference<Instant> now;

        private SettableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void advance(Duration amount) {
            now.updateAndGet(current -> current.plus(amount));
        }

        @Override
        public Instant instant() {
            return now.get();
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }
    }
}

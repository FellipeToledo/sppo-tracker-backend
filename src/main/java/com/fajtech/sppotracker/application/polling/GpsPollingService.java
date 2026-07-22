package com.fajtech.sppotracker.application.polling;

import com.fajtech.sppotracker.application.ingestion.GpsPositionIngestor;
import com.fajtech.sppotracker.application.port.in.GetGpsPollingStatusUseCase;
import com.fajtech.sppotracker.application.port.in.RunGpsPollingCycleUseCase;
import com.fajtech.sppotracker.application.port.out.FetchExternalGpsPositionsPort;
import com.fajtech.sppotracker.application.port.out.ProviderReadinessPort;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Orquestra um ciclo de polling de GPS (docs/regras-de-negocio.md §3.1):
 * verifica readiness, respeita o cooldown por falhas consecutivas, consulta a
 * janela com sobreposição {@code [now - overlap, now]} no provider e produz um
 * {@link PollingCycleResult}.
 *
 * <p>Classe pura de aplicação (sem framework); a instância é criada e vinculada
 * pela infraestrutura. O tempo vem de um {@link Clock} injetável (UTC).
 *
 * <p>Estado thread-safe: um ciclo não roda concorrente consigo mesmo (thread única
 * do scheduler), mas {@code lastResult()} pode ser lido por outras threads (REST).
 */
public class GpsPollingService implements RunGpsPollingCycleUseCase, GetGpsPollingStatusUseCase {

    private final FetchExternalGpsPositionsPort fetchPort;
    private final ProviderReadinessPort readinessPort;
    private final GpsPositionIngestor ingestor;
    private final Clock clock;
    private final Duration overlapWindow;
    private final int failureCooldownThreshold;
    private final Duration failureCooldown;

    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicReference<PollingCycleResult> lastResult = new AtomicReference<>();
    private volatile Instant cooldownUntil;

    public GpsPollingService(FetchExternalGpsPositionsPort fetchPort,
                             ProviderReadinessPort readinessPort,
                             GpsPositionIngestor ingestor,
                             Clock clock,
                             Duration overlapWindow,
                             int failureCooldownThreshold,
                             Duration failureCooldown) {
        this.fetchPort = Objects.requireNonNull(fetchPort, "fetchPort");
        this.readinessPort = Objects.requireNonNull(readinessPort, "readinessPort");
        this.ingestor = Objects.requireNonNull(ingestor, "ingestor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.overlapWindow = Objects.requireNonNull(overlapWindow, "overlapWindow");
        this.failureCooldownThreshold = failureCooldownThreshold;
        this.failureCooldown = Objects.requireNonNull(failureCooldown, "failureCooldown");
    }

    @Override
    public PollingCycleResult runCycle() {
        Instant startedAt = clock.instant();

        if (inCooldown(startedAt)) {
            return record(PollingCycleResult.skipped(
                    PollingSkipReason.FAILURE_COOLDOWN, consecutiveFailures.get(),
                    startedAt, elapsedSince(startedAt)));
        }

        if (!readinessPort.isReady()) {
            return record(PollingCycleResult.skipped(
                    PollingSkipReason.PROVIDER_NOT_READY, consecutiveFailures.get(),
                    startedAt, elapsedSince(startedAt)));
        }

        Instant windowEnd = startedAt;
        Instant windowStart = startedAt.minus(overlapWindow);
        try {
            List<VehiclePosition> positions = fetchPort.fetch(windowStart, windowEnd);
            // Hot path por posição: dedup → detecção de mudança → snapshot atual.
            // (classificação e publicação entram em fatias posteriores.)
            ingestor.ingest(positions);
            onSuccess();
            return record(PollingCycleResult.success(
                    windowStart, windowEnd, positions.size(), startedAt, elapsedSince(startedAt)));
        } catch (RuntimeException e) {
            int failures = onFailure(startedAt);
            return record(PollingCycleResult.failure(
                    windowStart, windowEnd, failures, startedAt, elapsedSince(startedAt), e.getMessage()));
        }
    }

    /** Último resultado de ciclo (para o endpoint de status, §7.1); null se nunca rodou. */
    public PollingCycleResult lastResult() {
        return lastResult.get();
    }

    @Override
    public Optional<PollingCycleResult> lastStatus() {
        return Optional.ofNullable(lastResult.get());
    }

    private boolean inCooldown(Instant now) {
        Instant until = cooldownUntil;
        return until != null && now.isBefore(until);
    }

    private void onSuccess() {
        consecutiveFailures.set(0);
        cooldownUntil = null;
    }

    private int onFailure(Instant now) {
        int failures = consecutiveFailures.incrementAndGet();
        if (failures >= failureCooldownThreshold) {
            cooldownUntil = now.plus(failureCooldown);
        }
        return failures;
    }

    private Duration elapsedSince(Instant startedAt) {
        return Duration.between(startedAt, clock.instant());
    }

    private PollingCycleResult record(PollingCycleResult result) {
        lastResult.set(result);
        return result;
    }
}

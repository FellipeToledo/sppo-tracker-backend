package com.fajtech.sppotracker.infrastructure.observability;

import com.fajtech.sppotracker.application.ingestion.IngestionResult;
import com.fajtech.sppotracker.application.port.out.IngestionMetricsPort;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Métricas Micrometer do hot path (docs/regras-de-negocio.md §7.3). Implementa a
 * porta de aplicação; expõe contadores de ingestão e classificação e a idade das
 * posições em {@code /actuator/prometheus}.
 */
@Component
public class MicrometerIngestionMetrics implements IngestionMetricsPort {

    private static final String POSITIONS = "gps.ingestion.positions";
    private static final String CLASSIFICATIONS = "gps.classifications";

    private final MeterRegistry registry;
    private final Counter received;
    private final Counter duplicated;
    private final Counter unchanged;
    private final Counter changed;
    private final Timer positionAge;

    public MicrometerIngestionMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.received = Counter.builder(POSITIONS).tag("result", "received").register(registry);
        this.duplicated = Counter.builder(POSITIONS).tag("result", "duplicated").register(registry);
        this.unchanged = Counter.builder(POSITIONS).tag("result", "unchanged").register(registry);
        this.changed = Counter.builder(POSITIONS).tag("result", "changed").register(registry);
        this.positionAge = Timer.builder("gps.position.age")
                .description("Idade da posição na ingestão (now − positionTimestamp)")
                .register(registry);
    }

    @Override
    public void recordBatch(IngestionResult result) {
        received.increment(result.received());
        duplicated.increment(result.duplicated());
        unchanged.increment(result.unchanged());
        changed.increment(result.changed());
    }

    @Override
    public void recordClassification(VehiclePositionStatus status) {
        Counter.builder(CLASSIFICATIONS).tag("status", status.name()).register(registry).increment();
    }

    @Override
    public void recordPositionAge(Duration age) {
        positionAge.record(age);
    }
}

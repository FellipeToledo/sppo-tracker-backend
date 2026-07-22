package com.fajtech.sppotracker.infrastructure.observability;

import com.fajtech.sppotracker.application.ingestion.IngestionResult;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Métricas de ingestão sobre um SimpleMeterRegistry (§7.3). */
class MicrometerIngestionMetricsTest {

    private SimpleMeterRegistry registry;
    private MicrometerIngestionMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerIngestionMetrics(registry);
    }

    @Test
    void shouldRecordBatchCounters() {
        metrics.recordBatch(new IngestionResult(10, 3, 2, 5));

        assertThat(counter("received")).isEqualTo(10.0);
        assertThat(counter("duplicated")).isEqualTo(3.0);
        assertThat(counter("unchanged")).isEqualTo(2.0);
        assertThat(counter("changed")).isEqualTo(5.0);
    }

    @Test
    void shouldAccumulateAcrossBatches() {
        metrics.recordBatch(new IngestionResult(1, 0, 0, 1));
        metrics.recordBatch(new IngestionResult(2, 1, 0, 1));

        assertThat(counter("received")).isEqualTo(3.0);
        assertThat(counter("changed")).isEqualTo(2.0);
    }

    @Test
    void shouldCountClassificationsByStatus() {
        metrics.recordClassification(VehiclePositionStatus.IN_OPERATION);
        metrics.recordClassification(VehiclePositionStatus.IN_OPERATION);
        metrics.recordClassification(VehiclePositionStatus.IN_GARAGE);

        assertThat(registry.get("gps.classifications")
                .tag("status", "IN_OPERATION").counter().count()).isEqualTo(2.0);
        assertThat(registry.get("gps.classifications")
                .tag("status", "IN_GARAGE").counter().count()).isEqualTo(1.0);
    }

    @Test
    void shouldRecordPositionAge() {
        metrics.recordPositionAge(Duration.ofSeconds(30));

        var timer = registry.get("gps.position.age").timer();
        assertThat(timer.count()).isEqualTo(1L);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isEqualTo(30.0);
    }

    private double counter(String result) {
        return registry.get("gps.ingestion.positions").tag("result", result).counter().count();
    }
}

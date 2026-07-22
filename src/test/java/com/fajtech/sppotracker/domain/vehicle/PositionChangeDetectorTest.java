package com.fajtech.sppotracker.domain.vehicle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Detecção de mudança vs. snapshot atual (docs/regras-de-negocio.md §3.3). */
class PositionChangeDetectorTest {

    private static final Instant T1 = Instant.parse("2026-07-22T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-22T12:00:20Z");
    private static final Coordinates C1 = new Coordinates(new BigDecimal("-22.900"), new BigDecimal("-43.200"));
    private static final Coordinates C2 = new Coordinates(new BigDecimal("-22.901"), new BigDecimal("-43.200"));

    private final PositionChangeDetector detector = new PositionChangeDetector();

    private static VehiclePosition.Builder base(Instant positionTs, Coordinates coords) {
        return VehiclePosition.builder()
                .vehicleId("A12345")
                .serviceCode("100")
                .coordinates(coords)
                .speed(30.0)
                .heading(90)
                .positionTimestamp(positionTs)
                .sentTimestamp(positionTs)
                .receivedAt(positionTs)
                .source(PositionSource.DADOS_MOBILIDADE_RIO);
    }

    @Test
    void shouldBeChangedWhenNoPreviousSnapshot() {
        assertThat(detector.hasChanged(base(T1, C1).build(), null)).isTrue();
    }

    @Test
    void shouldNotBeChangedWhenCandidateIsNotNewer() {
        VehiclePosition previous = base(T2, C1).build();
        VehiclePosition candidateSameTs = base(T2, C2).build(); // coords diferentes, mas mesmo ts
        VehiclePosition candidateOlder = base(T1, C2).build();

        assertThat(detector.hasChanged(candidateSameTs, previous)).isFalse();
        assertThat(detector.hasChanged(candidateOlder, previous)).isFalse();
    }

    @Test
    void shouldNotBeChangedWhenNewerButIdenticalRelevantFields() {
        VehiclePosition previous = base(T1, C1).build();
        VehiclePosition candidate = base(T2, C1).build(); // mais novo, mesmos coords/speed/heading/rota
        assertThat(detector.hasChanged(candidate, previous)).isFalse();
    }

    @Test
    void shouldBeChangedWhenNewerAndCoordinatesDiffer() {
        VehiclePosition previous = base(T1, C1).build();
        VehiclePosition candidate = base(T2, C2).build();
        assertThat(detector.hasChanged(candidate, previous)).isTrue();
    }

    @Test
    void shouldBeChangedWhenNewerAndSpeedDiffers() {
        VehiclePosition previous = base(T1, C1).build();
        VehiclePosition candidate = base(T2, C1).speed(45.0).build();
        assertThat(detector.hasChanged(candidate, previous)).isTrue();
    }

    @Test
    void shouldBeChangedWhenNewerAndHeadingDiffers() {
        VehiclePosition previous = base(T1, C1).build();
        VehiclePosition candidate = base(T2, C1).heading(180).build();
        assertThat(detector.hasChanged(candidate, previous)).isTrue();
    }

    @Test
    void shouldBeChangedWhenNewerAndRouteContextDiffers() {
        VehiclePosition previous = base(T1, C1).build();
        assertThat(detector.hasChanged(base(T2, C1).serviceCode("200").build(), previous)).isTrue();
        assertThat(detector.hasChanged(base(T2, C1).directionCode("1").build(), previous)).isTrue();
        assertThat(detector.hasChanged(base(T2, C1).routeId("R9").build(), previous)).isTrue();
        assertThat(detector.hasChanged(base(T2, C1).tripId("T9").build(), previous)).isTrue();
        assertThat(detector.hasChanged(base(T2, C1).shapeId("S9").build(), previous)).isTrue();
    }
}

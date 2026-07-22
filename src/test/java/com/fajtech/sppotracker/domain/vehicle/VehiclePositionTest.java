package com.fajtech.sppotracker.domain.vehicle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes do value object VehiclePosition (docs/regras-de-negocio.md §2.1). */
class VehiclePositionTest {

    private static final Coordinates RIO = new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2"));
    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    private static VehiclePosition.Builder valid() {
        return VehiclePosition.builder()
                .vehicleId("A12345")
                .coordinates(RIO)
                .positionTimestamp(NOW)
                .sentTimestamp(NOW)
                .receivedAt(NOW)
                .source(PositionSource.DADOS_MOBILIDADE_RIO);
    }

    @Test
    void shouldBuildWithOnlyRequiredFields() {
        VehiclePosition position = valid().build();

        assertThat(position.vehicleId()).isEqualTo("A12345");
        assertThat(position.coordinates()).isEqualTo(RIO);
        assertThat(position.source()).isEqualTo(PositionSource.DADOS_MOBILIDADE_RIO);
        assertThat(position.serviceCode()).isNull();
        assertThat(position.directionCode()).isNull();
        assertThat(position.routeId()).isNull();
        assertThat(position.tripId()).isNull();
        assertThat(position.shapeId()).isNull();
        assertThat(position.heading()).isNull();
        assertThat(position.speed()).isNull();
        assertThat(position.serverTimestamp()).isNull();
    }

    @Test
    void shouldRejectNullVehicleId() {
        assertThatThrownBy(() -> valid().vehicleId(null).build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankVehicleId() {
        assertThatThrownBy(() -> valid().vehicleId("   ").build())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldTrimVehicleId() {
        assertThat(valid().vehicleId("  A12345  ").build().vehicleId()).isEqualTo("A12345");
    }

    @Test
    void shouldRejectNullCoordinates() {
        assertThatThrownBy(() -> valid().coordinates(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullPositionTimestamp() {
        assertThatThrownBy(() -> valid().positionTimestamp(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullSentTimestamp() {
        assertThatThrownBy(() -> valid().sentTimestamp(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullReceivedAt() {
        assertThatThrownBy(() -> valid().receivedAt(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullSource() {
        assertThatThrownBy(() -> valid().source(null).build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldNormalizeNegativeSpeedToNull() {
        assertThat(valid().speed(-1.0).build().speed()).isNull();
    }

    @Test
    void shouldKeepNonNegativeSpeed() {
        assertThat(valid().speed(0.0).build().speed()).isEqualTo(0.0);
        assertThat(valid().speed(42.5).build().speed()).isEqualTo(42.5);
    }

    @Test
    void shouldPreserveOptionalFieldsWhenPresent() {
        VehiclePosition position = valid()
                .serviceCode("100")
                .directionCode("0")
                .routeId("R100")
                .tripId("T1")
                .shapeId("S1")
                .heading(180)
                .speed(30.0)
                .serverTimestamp(NOW)
                .build();

        assertThat(position.serviceCode()).isEqualTo("100");
        assertThat(position.directionCode()).isEqualTo("0");
        assertThat(position.routeId()).isEqualTo("R100");
        assertThat(position.tripId()).isEqualTo("T1");
        assertThat(position.shapeId()).isEqualTo("S1");
        assertThat(position.heading()).isEqualTo(180);
        assertThat(position.speed()).isEqualTo(30.0);
        assertThat(position.serverTimestamp()).isEqualTo(NOW);
    }
}

package com.fajtech.sppotracker.domain.vehicle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Bounding box do município (docs/regras-de-negocio.md §4.3). */
class BoundingBoxTest {

    // box do Rio (defaults §4.3)
    private final BoundingBox rio = new BoundingBox(
            new BigDecimal("-23.10"), new BigDecimal("-22.70"),
            new BigDecimal("-43.80"), new BigDecimal("-43.05"));

    private static Coordinates at(String lat, String lon) {
        return new Coordinates(new BigDecimal(lat), new BigDecimal(lon));
    }

    @Test
    void shouldContainPointInsideBox() {
        assertThat(rio.contains(at("-22.90", "-43.20"))).isTrue();
    }

    @Test
    void shouldContainPointsOnTheEdges() {
        assertThat(rio.contains(at("-23.10", "-43.80"))).isTrue();
        assertThat(rio.contains(at("-22.70", "-43.05"))).isTrue();
    }

    @Test
    void shouldNotContainPointOutsideBox() {
        assertThat(rio.contains(at("-22.50", "-43.20"))).isFalse(); // norte demais
        assertThat(rio.contains(at("-22.90", "-44.00"))).isFalse(); // oeste demais
        assertThat(rio.contains(at("-23.50", "-43.20"))).isFalse(); // sul demais
    }
}

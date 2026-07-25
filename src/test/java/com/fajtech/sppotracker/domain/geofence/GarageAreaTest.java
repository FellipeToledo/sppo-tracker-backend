package com.fajtech.sppotracker.domain.geofence;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GarageAreaTest {

    private static Coordinates c(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    /** Quadrado de ~0,01° de lado (≈1,1 km), anel fechado. */
    private static GarageArea square() {
        return GarageArea.of("g1", "Garagem Teste", List.of(
                c(-22.90, -43.20),
                c(-22.90, -43.19),
                c(-22.89, -43.19),
                c(-22.89, -43.20),
                c(-22.90, -43.20)));
    }

    @Test
    void shouldContainPointInsidePolygon() {
        assertThat(square().contains(c(-22.895, -43.195))).isTrue();
    }

    @Test
    void shouldNotContainPointOutsidePolygon() {
        assertThat(square().contains(c(-22.895, -43.18))).isFalse();
    }

    @Test
    void shouldTreatPointOnBoundaryAsInside() {
        // Decisão explícita: a borda conta como dentro — o ray casting puro é
        // indefinido na aresta, e para garagem o conservador é classificar dentro.
        GarageArea area = square();
        assertThat(area.contains(c(-22.90, -43.195))).as("aresta inferior").isTrue();
        assertThat(area.contains(c(-22.90, -43.20))).as("vértice").isTrue();
    }

    @Test
    void shouldRejectPointOutsideBoundingBoxWithoutScanningRing() {
        assertThat(square().contains(c(-22.70, -43.90))).isFalse();
    }

    @Test
    void shouldNotContainPointInsideConcavity() {
        // Polígono em "L": o quadrante nordeste está recortado. Um convex hull
        // classificaria o ponto do recorte como dentro; o ray casting não.
        GarageArea l = GarageArea.of("g2", "Pátio em L", List.of(
                c(-22.90, -43.20),
                c(-22.90, -43.18),
                c(-22.895, -43.18),
                c(-22.895, -43.19),
                c(-22.89, -43.19),
                c(-22.89, -43.20),
                c(-22.90, -43.20)));
        assertThat(l.contains(c(-22.8925, -43.185))).as("recorte").isFalse();
        assertThat(l.contains(c(-22.8975, -43.185))).as("braço inferior").isTrue();
    }

    @Test
    void shouldCloseAnOpenRing() {
        GarageArea open = GarageArea.of("g3", "Anel aberto", List.of(
                c(-22.90, -43.20),
                c(-22.90, -43.19),
                c(-22.89, -43.19),
                c(-22.89, -43.20)));
        assertThat(open.ring()).hasSize(5);
        assertThat(open.ring().getFirst()).isEqualTo(open.ring().getLast());
        assertThat(open.contains(c(-22.895, -43.195))).isTrue();
    }

    @Test
    void shouldDeriveBoundingBoxFromRing() {
        assertThat(square().bounds().minLatitude()).isEqualByComparingTo("-22.90");
        assertThat(square().bounds().maxLatitude()).isEqualByComparingTo("-22.89");
        assertThat(square().bounds().minLongitude()).isEqualByComparingTo("-43.20");
        assertThat(square().bounds().maxLongitude()).isEqualByComparingTo("-43.19");
    }

    @Test
    void shouldRejectRingWithFewerThanThreeDistinctPoints() {
        assertThatThrownBy(() -> GarageArea.of("g4", "Degenerada", List.of(
                c(-22.90, -43.20), c(-22.89, -43.19))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 3");
    }

    @Test
    void shouldRejectBlankIdentification() {
        List<Coordinates> ring = square().ring();
        assertThatThrownBy(() -> GarageArea.of(" ", "Sem id", ring))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GarageArea.of("g5", " ", ring))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

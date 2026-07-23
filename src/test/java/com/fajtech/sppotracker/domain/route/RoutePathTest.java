package com.fajtech.sppotracker.domain.route;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RoutePathTest {

    private static Coordinates at(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    // Segmento horizontal (~1 km ao longo de latitude constante) no Rio.
    private static RoutePath horizontalSegment() {
        return RoutePath.of("s1", List.of(at(-22.9000, -43.2000), at(-22.9000, -43.1900)));
    }

    @Test
    void shouldComputeBoundingBoxFromPoints() {
        RoutePath path = horizontalSegment();
        assertThat(path.bbox().minLongitude()).isEqualByComparingTo("-43.2000");
        assertThat(path.bbox().maxLongitude()).isEqualByComparingTo("-43.1900");
    }

    @Test
    void shouldBeWithinCorridorForPointCloseToSegment() {
        // ~5,6 m ao norte do segmento (0,00005° de latitude).
        Coordinates near = at(-22.90005, -43.1950);
        assertThat(horizontalSegment().withinCorridor(near, 15.0)).isTrue();
    }

    @Test
    void shouldBeOutsideCorridorForDistantPoint() {
        // ~222 m ao norte do segmento (0,0020° de latitude).
        Coordinates far = at(-22.9020, -43.1950);
        assertThat(horizontalSegment().withinCorridor(far, 15.0)).isFalse();
        assertThat(horizontalSegment().distanceMeters(far)).isGreaterThan(200.0);
    }

    @Test
    void shouldRejectBboxPreFilterCheaplyForFarAwayPoint() {
        Coordinates farAway = at(-22.8000, -43.0000);
        assertThat(horizontalSegment().withinCorridor(farAway, 15.0)).isFalse();
    }

    @Test
    void shouldMeasureDistanceToSinglePointPath() {
        RoutePath point = RoutePath.of("p", List.of(at(-22.9000, -43.2000)));
        // ~111 m ao sul (0,001° de latitude).
        assertThat(point.distanceMeters(at(-22.9010, -43.2000))).isBetween(105.0, 115.0);
    }

    @Test
    void shouldRejectEmptyPoints() {
        assertThatThrownBy(() -> RoutePath.of("x", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

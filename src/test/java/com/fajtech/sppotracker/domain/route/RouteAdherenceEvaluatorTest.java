package com.fajtech.sppotracker.domain.route;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteAdherenceEvaluatorTest {

    private static final double CORRIDOR = 15.0;
    private static final double FALLBACK = 100.0;

    private static Coordinates at(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    private static RoutePath segment() {
        return RoutePath.of("s1", List.of(at(-22.9000, -43.2000), at(-22.9000, -43.1900)));
    }

    private static RouteAdherenceEvaluator evaluatorWith(RouteGeometrySource source) {
        return new RouteAdherenceEvaluator(source, CORRIDOR, FALLBACK);
    }

    @Test
    void shouldReportInsideWithSmallDistance() {
        RouteAdherence a = evaluatorWith(s -> List.of(segment())).evaluate("100", at(-22.90005, -43.1950));
        assertThat(a.isInside()).isTrue();
        assertThat(a.distanceMeters()).isLessThan(CORRIDOR);
    }

    @Test
    void shouldReportOutsideWithMagnitude() {
        RouteAdherence a = evaluatorWith(s -> List.of(segment())).evaluate("100", at(-22.9020, -43.1950));
        assertThat(a.isOutside()).isTrue();
        assertThat(a.distanceMeters()).isGreaterThan(200.0);
    }

    @Test
    void shouldReportUnresolvedWhenNoGeometry() {
        assertThat(evaluatorWith(s -> List.of()).evaluate("100", at(-22.9, -43.2)).isUnresolved()).isTrue();
    }

    @Test
    void shouldReportUnresolvedForBlankServiceOrZeroZero() {
        RouteAdherenceEvaluator e = evaluatorWith(s -> List.of(segment()));
        assertThat(e.evaluate(" ", at(-22.9, -43.2)).isUnresolved()).isTrue();
        assertThat(e.evaluate("100", at(0, 0)).isUnresolved()).isTrue();
    }

    @Test
    void shouldReportClosestDistanceAcrossShapes() {
        RoutePath near = segment();
        RoutePath far = RoutePath.of("s2", List.of(at(-22.9500, -43.3000), at(-22.9500, -43.2900)));
        RouteAdherence a = evaluatorWith(s -> List.of(far, near)).evaluate("100", at(-22.90005, -43.1950));
        assertThat(a.isInside()).isTrue();
    }
}

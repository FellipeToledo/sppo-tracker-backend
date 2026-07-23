package com.fajtech.sppotracker.domain.vehicle;

import com.fajtech.sppotracker.domain.route.RouteGeometrySource;
import com.fajtech.sppotracker.domain.route.RoutePath;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OutOfRouteRuleTest {

    private static final Instant NOW = Instant.parse("2026-07-23T12:00:00Z");
    private static final double CORRIDOR = 15.0;
    private static final double FALLBACK = 100.0;

    private static Coordinates at(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    private static RoutePath segment() {
        return RoutePath.of("s1", List.of(at(-22.9000, -43.2000), at(-22.9000, -43.1900)));
    }

    private static VehiclePosition positionAt(String serviceCode, Coordinates coordinates) {
        return VehiclePosition.builder()
                .vehicleId("A1001")
                .serviceCode(serviceCode)
                .coordinates(coordinates)
                .positionTimestamp(NOW)
                .sentTimestamp(NOW)
                .receivedAt(NOW)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    private static OutOfRouteRule ruleWith(RouteGeometrySource source) {
        return new OutOfRouteRule(source, CORRIDOR, FALLBACK);
    }

    @Test
    void shouldNotTagWhenPositionIsWithinAnyShapeCorridor() {
        OutOfRouteRule rule = ruleWith(service -> List.of(segment()));
        var tags = rule.evaluate(positionAt("100", at(-22.90005, -43.1950)), NOW);
        assertThat(tags).isEmpty();
    }

    @Test
    void shouldTagOutOfRouteWhenPositionIsOutsideAllShapes() {
        OutOfRouteRule rule = ruleWith(service -> List.of(segment()));
        var tags = rule.evaluate(positionAt("100", at(-22.9020, -43.1950)), NOW);
        assertThat(tags).containsExactly(ClassificationTag.OUT_OF_ROUTE);
    }

    @Test
    void shouldStayNeutralWhenGeometryUnresolved() {
        OutOfRouteRule rule = ruleWith(service -> List.of());
        var tags = rule.evaluate(positionAt("100", at(-22.9020, -43.1950)), NOW);
        assertThat(tags).isEmpty();
    }

    @Test
    void shouldNotTagWhenServiceCodeIsBlank() {
        OutOfRouteRule rule = ruleWith(service -> {
            throw new AssertionError("geometry must not be queried for a blank service code");
        });
        var tags = rule.evaluate(positionAt(" ", at(-22.9020, -43.1950)), NOW);
        assertThat(tags).isEmpty();
    }

    @Test
    void shouldStayInRouteWhenAnyOfSeveralShapesMatches() {
        RoutePath ida = segment();
        RoutePath volta = RoutePath.of("s2", List.of(at(-22.9500, -43.3000), at(-22.9500, -43.2900)));
        OutOfRouteRule rule = ruleWith(service -> List.of(volta, ida));
        var tags = rule.evaluate(positionAt("100", at(-22.90005, -43.1950)), NOW);
        assertThat(tags).isEmpty();
    }
}

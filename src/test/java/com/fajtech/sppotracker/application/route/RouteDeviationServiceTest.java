package com.fajtech.sppotracker.application.route;

import com.fajtech.sppotracker.application.port.out.PublishRouteDeviationEventPort;
import com.fajtech.sppotracker.application.port.out.RecordRouteDeviationEventPort;
import com.fajtech.sppotracker.application.port.out.RouteDeviationMetricsPort;
import com.fajtech.sppotracker.domain.route.DeviationConfig;
import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.RouteAdherenceEvaluator;
import com.fajtech.sppotracker.domain.route.RouteDeviationDetector;
import com.fajtech.sppotracker.domain.route.RouteDeviationEvent;
import com.fajtech.sppotracker.domain.route.RouteGeometrySource;
import com.fajtech.sppotracker.domain.route.RoutePath;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDeviationServiceTest {

    private static final Instant T0 = Instant.parse("2026-07-24T12:00:00Z");
    private static final DeviationConfig CFG =
            new DeviationConfig(30.0, 3, Duration.ofMinutes(3), 150.0, 3, 150.0, 500.0);

    private final List<RouteDeviationEvent> recorded = new ArrayList<>();
    private final List<RouteDeviationEvent> published = new ArrayList<>();
    private final List<DeviationEventType> metered = new ArrayList<>();
    private final RecordRouteDeviationEventPort recorder = recorded::add;
    private final PublishRouteDeviationEventPort publisher = published::add;
    private final RouteDeviationMetricsPort metrics = (type, severity) -> metered.add(type);

    private static Coordinates at(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    private static RoutePath segment() {
        return RoutePath.of("s1", List.of(at(-22.9000, -43.2000), at(-22.9000, -43.1900)));
    }

    private static VehiclePosition positionAt(Coordinates coordinates) {
        return VehiclePosition.builder()
                .vehicleId("A1001").serviceCode("100")
                .coordinates(coordinates)
                .positionTimestamp(T0).sentTimestamp(T0).receivedAt(T0)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    private RouteDeviationService serviceWith(RouteGeometrySource geometry) {
        RouteAdherenceEvaluator evaluator = new RouteAdherenceEvaluator(geometry, 15.0, 100.0);
        return new RouteDeviationService(evaluator, new RouteDeviationDetector(), CFG,
                Clock.fixed(T0, ZoneOffset.UTC), Duration.ofHours(6), recorder, publisher, metrics);
    }

    @Test
    void shouldEmitAlertAfterThreeOutsideObservations() {
        RouteDeviationService service = serviceWith(s -> List.of(segment()));
        Coordinates offRoute = at(-22.90050, -43.1950); // ~55 m fora (> margem 30, < confirm 150)

        service.onPosition(positionAt(offRoute));
        service.onPosition(positionAt(offRoute));
        service.onPosition(positionAt(offRoute));

        assertThat(recorded).hasSize(1);
        assertThat(recorded.getFirst().type()).isEqualTo(DeviationEventType.ALERT);
        assertThat(published).containsExactlyElementsOf(recorded);
        assertThat(metered).containsExactly(DeviationEventType.ALERT);
        assertThat(service.trackedVehicles()).isEqualTo(1);
    }

    @Test
    void shouldNotEmitWhileOnRoute() {
        RouteDeviationService service = serviceWith(s -> List.of(segment()));
        Coordinates onRoute = at(-22.90005, -43.1950); // ~5,5 m dentro do corredor

        for (int i = 0; i < 5; i++) {
            service.onPosition(positionAt(onRoute));
        }
        assertThat(recorded).isEmpty();
        assertThat(published).isEmpty();
    }

    @Test
    void shouldIgnorePositionWhenGeometryUnresolved() {
        RouteDeviationService service = serviceWith(s -> List.of());
        service.onPosition(positionAt(at(-22.90050, -43.1950)));

        assertThat(recorded).isEmpty();
        assertThat(service.trackedVehicles()).isZero();
    }
}

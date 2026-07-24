package com.fajtech.sppotracker.domain.route;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RouteDeviationDetectorTest {

    private static final Instant T0 = Instant.parse("2026-07-24T12:00:00Z");
    private static final DeviationConfig CFG =
            new DeviationConfig(30.0, 3, Duration.ofMinutes(3), 150.0, 3, 150.0, 500.0);

    private final RouteDeviationDetector detector = new RouteDeviationDetector();

    private static VehiclePosition vehicle() {
        return VehiclePosition.builder()
                .vehicleId("A1001").serviceCode("100")
                .coordinates(new Coordinates(BigDecimal.valueOf(-22.9), BigDecimal.valueOf(-43.2)))
                .positionTimestamp(T0).sentTimestamp(T0).receivedAt(T0)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    private DeviationOutcome observe(VehicleDeviationState state, RouteAdherence adherence, Instant at) {
        return detector.observe(state, vehicle(), adherence, at, CFG);
    }

    @Test
    void shouldOpenEpisodeWithAlertAfterThreeOutsidePoints() {
        VehicleDeviationState state = VehicleDeviationState.initial();
        state = observe(state, RouteAdherence.outside(50), T0).state();
        state = observe(state, RouteAdherence.outside(50), T0.plusSeconds(30)).state();
        DeviationOutcome third = observe(state, RouteAdherence.outside(50), T0.plusSeconds(60));

        assertThat(third.events()).hasSize(1);
        assertThat(third.events().getFirst().type()).isEqualTo(DeviationEventType.ALERT);
        assertThat(third.events().getFirst().severity()).isEqualTo(DeviationSeverity.LEVE);
        assertThat(third.state().phase()).isEqualTo(DeviationPhase.OFF_ROUTE);
        assertThat(third.state().confirmed()).isFalse();
    }

    @Test
    void shouldTreatEdgeSkimmingBelowMarginAsInside() {
        VehicleDeviationState state = VehicleDeviationState.initial();
        for (int i = 0; i < 6; i++) {
            DeviationOutcome out = observe(state, RouteAdherence.outside(20), T0.plusSeconds(i * 30L));
            assertThat(out.events()).isEmpty();
            state = out.state();
        }
        assertThat(state.phase()).isEqualTo(DeviationPhase.ON_ROUTE);
    }

    @Test
    void shouldConfirmImmediatelyWhenDistanceExceedsConfirmThreshold() {
        VehicleDeviationState state = VehicleDeviationState.initial();
        state = observe(state, RouteAdherence.outside(200), T0).state();
        state = observe(state, RouteAdherence.outside(200), T0.plusSeconds(30)).state();
        DeviationOutcome third = observe(state, RouteAdherence.outside(200), T0.plusSeconds(60));

        assertThat(third.events()).extracting(RouteDeviationEvent::type)
                .containsExactly(DeviationEventType.ALERT, DeviationEventType.CONFIRMED);
        assertThat(third.events()).allSatisfy(e ->
                assertThat(e.severity()).isEqualTo(DeviationSeverity.MEDIO));
        assertThat(third.state().confirmed()).isTrue();
    }

    @Test
    void shouldConfirmWhenSustainedOverTime() {
        VehicleDeviationState state = openAlert();          // ALERT em T0+60s, não confirmado
        Instant sustained = T0.plusSeconds(60).plus(Duration.ofMinutes(3));
        DeviationOutcome out = observe(state, RouteAdherence.outside(50), sustained);

        assertThat(out.events()).extracting(RouteDeviationEvent::type)
                .containsExactly(DeviationEventType.CONFIRMED);
        assertThat(out.state().confirmed()).isTrue();
    }

    @Test
    void shouldCloseWithReturnAfterConfirmedThenThreeInsidePoints() {
        VehicleDeviationState state = confirmedByDistance();  // ALERT+CONFIRMED
        state = observe(state, RouteAdherence.inside(5), T0.plusSeconds(90)).state();
        state = observe(state, RouteAdherence.inside(5), T0.plusSeconds(120)).state();
        DeviationOutcome third = observe(state, RouteAdherence.inside(5), T0.plusSeconds(150));

        assertThat(third.events()).extracting(RouteDeviationEvent::type)
                .containsExactly(DeviationEventType.RETURN);
        assertThat(third.state().phase()).isEqualTo(DeviationPhase.ON_ROUTE);
        assertThat(third.state().confirmed()).isFalse();
    }

    @Test
    void shouldCancelWhenReturningBeforeConfirmed() {
        VehicleDeviationState state = openAlert();            // só ALERT
        state = observe(state, RouteAdherence.inside(5), T0.plusSeconds(90)).state();
        state = observe(state, RouteAdherence.inside(5), T0.plusSeconds(120)).state();
        DeviationOutcome third = observe(state, RouteAdherence.inside(5), T0.plusSeconds(150));

        assertThat(third.events()).extracting(RouteDeviationEvent::type)
                .containsExactly(DeviationEventType.CANCELLED);
        assertThat(third.state().phase()).isEqualTo(DeviationPhase.ON_ROUTE);
    }

    @Test
    void sweepShouldConfirmSilentVehicleAfterSustainedTime() {
        VehicleDeviationState state = openAlert();            // ALERT em T0+60s

        DeviationOutcome early = detector.sweep(state, "A1001", T0.plusSeconds(90), CFG);
        assertThat(early.events()).isEmpty();

        Instant sustained = T0.plusSeconds(60).plus(Duration.ofMinutes(3));
        DeviationOutcome late = detector.sweep(state, "A1001", sustained, CFG);
        assertThat(late.events()).extracting(RouteDeviationEvent::type)
                .containsExactly(DeviationEventType.CONFIRMED);
        assertThat(late.state().confirmed()).isTrue();
    }

    @Test
    void shouldStayUnchangedOnUnresolvedAdherence() {
        VehicleDeviationState state = VehicleDeviationState.initial();
        DeviationOutcome out = observe(state, RouteAdherence.unresolved(), T0);
        assertThat(out.events()).isEmpty();
        assertThat(out.state()).isEqualTo(state);
    }

    // --- helpers de cenário ---

    private VehicleDeviationState openAlert() {
        VehicleDeviationState state = VehicleDeviationState.initial();
        state = observe(state, RouteAdherence.outside(50), T0).state();
        state = observe(state, RouteAdherence.outside(50), T0.plusSeconds(30)).state();
        return observe(state, RouteAdherence.outside(50), T0.plusSeconds(60)).state();
    }

    private VehicleDeviationState confirmedByDistance() {
        VehicleDeviationState state = VehicleDeviationState.initial();
        state = observe(state, RouteAdherence.outside(200), T0).state();
        state = observe(state, RouteAdherence.outside(200), T0.plusSeconds(30)).state();
        return observe(state, RouteAdherence.outside(200), T0.plusSeconds(60)).state();
    }
}

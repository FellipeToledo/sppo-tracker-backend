package com.fajtech.sppotracker.application.route;

import com.fajtech.sppotracker.application.port.out.PublishRouteDeviationEventPort;
import com.fajtech.sppotracker.application.port.out.RecordRouteDeviationEventPort;
import com.fajtech.sppotracker.application.port.out.RouteDeviationMetricsPort;
import com.fajtech.sppotracker.domain.route.DeviationConfig;
import com.fajtech.sppotracker.domain.route.DeviationOutcome;
import com.fajtech.sppotracker.domain.route.RouteAdherence;
import com.fajtech.sppotracker.domain.route.RouteAdherenceEvaluator;
import com.fajtech.sppotracker.domain.route.RouteDeviationDetector;
import com.fajtech.sppotracker.domain.route.RouteDeviationEvent;
import com.fajtech.sppotracker.domain.route.VehicleDeviationState;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquestra a detecção de desvio de itinerário (docs/regras-de-negocio.md §5), fora
 * do hot path de polling: consome o stream de posições já publicado, avalia a
 * aderência (com distância) e evolui a máquina de estados por veículo, persistindo e
 * publicando os eventos emitidos. Classe de aplicação sem framework.
 *
 * <p>O estado por veículo fica num mapa concorrente; as transições por veículo são
 * serializadas via {@link ConcurrentHashMap#compute} (o I/O de persistência/publicação
 * roda <b>fora</b> do lock). Estados sem observação há mais que o TTL são podados no sweep.
 */
public final class RouteDeviationService {

    private final RouteAdherenceEvaluator evaluator;
    private final RouteDeviationDetector detector;
    private final DeviationConfig config;
    private final Clock clock;
    private final Duration stateTtl;
    private final RecordRouteDeviationEventPort recorder;
    private final PublishRouteDeviationEventPort publisher;
    private final RouteDeviationMetricsPort metrics;

    private final Map<String, VehicleDeviationState> states = new ConcurrentHashMap<>();

    public RouteDeviationService(RouteAdherenceEvaluator evaluator, RouteDeviationDetector detector,
                                 DeviationConfig config, Clock clock, Duration stateTtl,
                                 RecordRouteDeviationEventPort recorder, PublishRouteDeviationEventPort publisher,
                                 RouteDeviationMetricsPort metrics) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
        this.detector = Objects.requireNonNull(detector, "detector");
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.stateTtl = Objects.requireNonNull(stateTtl, "stateTtl");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
    }

    /** Processa uma observação de posição e emite os eventos de desvio resultantes. */
    public void onPosition(VehiclePosition position) {
        RouteAdherence adherence = evaluator.evaluate(position.serviceCode(), position.coordinates());
        if (adherence.isUnresolved()) {
            return; // geometria ainda não em cache: espera o próximo ciclo (§5.1)
        }
        Instant now = clock.instant();
        List<RouteDeviationEvent> emitted = new ArrayList<>();
        states.compute(position.vehicleId(), (vehicleId, previous) -> {
            VehicleDeviationState base = previous == null ? VehicleDeviationState.initial() : previous;
            DeviationOutcome outcome = detector.observe(base, position, adherence, now, config);
            emitted.addAll(outcome.events());
            return outcome.state();
        });
        dispatch(emitted);
    }

    /** Consolida episódios de veículos silenciosos e poda estados expirados (§5.3). */
    public void sweep() {
        Instant now = clock.instant();
        List<RouteDeviationEvent> emitted = new ArrayList<>();
        for (String vehicleId : states.keySet()) {
            states.compute(vehicleId, (id, previous) -> {
                if (previous == null || isExpired(previous, now)) {
                    return null; // remove a entrada
                }
                DeviationOutcome outcome = detector.sweep(previous, id, now, config);
                emitted.addAll(outcome.events());
                return outcome.state();
            });
        }
        dispatch(emitted);
    }

    int trackedVehicles() {
        return states.size();
    }

    private boolean isExpired(VehicleDeviationState state, Instant now) {
        Instant last = state.lastObservedAt();
        return last != null && Duration.between(last, now).compareTo(stateTtl) > 0;
    }

    private void dispatch(List<RouteDeviationEvent> events) {
        for (RouteDeviationEvent event : events) {
            recorder.record(event);
            publisher.publish(event);
            metrics.recordEmitted(event.type(), event.severity());
        }
    }
}

package com.fajtech.sppotracker.domain.route;

import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Máquina de estados <b>pura</b> de desvio de itinerário por veículo
 * (docs/regras-de-negocio.md §5.3). Transforma a sequência de observações
 * dentro/fora do corredor em episódios (ALERT → CONFIRMED → RETURN/CANCELLED), com
 * histerese temporal. Sem estado próprio, sem framework: recebe o estado anterior e a
 * observação, devolve o novo estado + eventos.
 *
 * <p>Regras: um ponto conta como fora só se {@code OUTSIDE} <b>e</b> distância maior que
 * a margem de confirmação (§5.2). {@code ALERT} abre o episódio após N pontos fora;
 * {@code CONFIRMED} (uma vez) quando sustentado por tempo ou distância; {@code RETURN}
 * fecha após N pontos dentro se houve CONFIRMED, senão {@code CANCELLED}. O {@link #sweep}
 * consolida episódios de veículos que ficaram em silêncio fora do corredor.
 */
public class RouteDeviationDetector {

    /** Processa uma observação de aderência e evolui o estado do veículo. */
    public DeviationOutcome observe(VehicleDeviationState prev, VehiclePosition position,
                                    RouteAdherence adherence, Instant timestamp, DeviationConfig cfg) {
        if (adherence.isUnresolved()) {
            return new DeviationOutcome(prev, List.of());
        }

        boolean outside = adherence.isOutside()
                && adherence.distanceMeters() > cfg.confirmationMarginMeters();

        DeviationPhase phase = prev.phase();
        int consecutiveOutside = prev.consecutiveOutside();
        int consecutiveInside = prev.consecutiveInside();
        boolean alertEmitted = prev.alertEmitted();
        boolean confirmed = prev.confirmed();
        Instant episodeStart = prev.episodeStart();
        double maxDistance = prev.maxDistanceMeters();

        if (outside) {
            consecutiveOutside += 1;
            consecutiveInside = 0;
            maxDistance = Math.max(maxDistance, adherence.distanceMeters());
        } else {
            consecutiveInside += 1;
            consecutiveOutside = 0;
        }

        List<RouteDeviationEvent> events = new ArrayList<>();

        if (phase == DeviationPhase.ON_ROUTE) {
            if (outside && consecutiveOutside >= cfg.alertPoints()) {
                phase = DeviationPhase.OFF_ROUTE;
                alertEmitted = true;
                confirmed = false;
                episodeStart = timestamp;
                events.add(event(position, DeviationEventType.ALERT, maxDistance, timestamp, cfg));
                if (maxDistance > cfg.confirmDistanceMeters()) {
                    confirmed = true;
                    events.add(event(position, DeviationEventType.CONFIRMED, maxDistance, timestamp, cfg));
                }
            } else if (!outside) {
                maxDistance = 0.0; // sem corrida fora ativa
            }
        } else { // OFF_ROUTE
            if (outside) {
                if (!confirmed && shouldConfirm(episodeStart, timestamp, maxDistance, cfg)) {
                    confirmed = true;
                    events.add(event(position, DeviationEventType.CONFIRMED, maxDistance, timestamp, cfg));
                }
            } else if (consecutiveInside >= cfg.returnPoints()) {
                events.add(event(position,
                        confirmed ? DeviationEventType.RETURN : DeviationEventType.CANCELLED,
                        maxDistance, timestamp, cfg));
                return new DeviationOutcome(closed(position, timestamp), events);
            }
        }

        VehicleDeviationState next = new VehicleDeviationState(
                phase, consecutiveOutside, consecutiveInside, alertEmitted, confirmed,
                episodeStart, maxDistance, timestamp, position.serviceCode(), position.routeId());
        return new DeviationOutcome(next, events);
    }

    /**
     * Consolida episódios abertos de veículos silenciosos (§5.3): se já decorreu o
     * tempo sustentado desde a abertura e ainda não houve CONFIRMED, emite CONFIRMED.
     */
    public DeviationOutcome sweep(VehicleDeviationState prev, String vehicleId, Instant now, DeviationConfig cfg) {
        if (prev.phase() != DeviationPhase.OFF_ROUTE || prev.confirmed() || prev.episodeStart() == null) {
            return new DeviationOutcome(prev, List.of());
        }
        if (Duration.between(prev.episodeStart(), now).compareTo(cfg.confirmSustained()) < 0) {
            return new DeviationOutcome(prev, List.of());
        }
        RouteDeviationEvent event = new RouteDeviationEvent(
                vehicleId, prev.serviceCode(), prev.routeId(), DeviationEventType.CONFIRMED,
                DeviationSeverity.fromMaxDistance(prev.maxDistanceMeters(),
                        cfg.severityMedioMeters(), cfg.severityGraveMeters()),
                prev.maxDistanceMeters(), now);
        VehicleDeviationState next = new VehicleDeviationState(
                prev.phase(), prev.consecutiveOutside(), prev.consecutiveInside(),
                prev.alertEmitted(), true, prev.episodeStart(), prev.maxDistanceMeters(),
                prev.lastObservedAt(), prev.serviceCode(), prev.routeId());
        return new DeviationOutcome(next, List.of(event));
    }

    private static boolean shouldConfirm(Instant episodeStart, Instant now, double maxDistance, DeviationConfig cfg) {
        boolean sustained = episodeStart != null
                && Duration.between(episodeStart, now).compareTo(cfg.confirmSustained()) >= 0;
        return sustained || maxDistance > cfg.confirmDistanceMeters();
    }

    private static VehicleDeviationState closed(VehiclePosition position, Instant timestamp) {
        return new VehicleDeviationState(
                DeviationPhase.ON_ROUTE, 0, 0, false, false, null, 0.0,
                timestamp, position.serviceCode(), position.routeId());
    }

    private static RouteDeviationEvent event(VehiclePosition position, DeviationEventType type,
                                             double maxDistance, Instant timestamp, DeviationConfig cfg) {
        return new RouteDeviationEvent(
                position.vehicleId(), position.serviceCode(), position.routeId(), type,
                DeviationSeverity.fromMaxDistance(maxDistance, cfg.severityMedioMeters(), cfg.severityGraveMeters()),
                maxDistance, timestamp);
    }
}

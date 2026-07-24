package com.fajtech.sppotracker.domain.route;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;

import java.util.List;
import java.util.Objects;

/**
 * Avaliador de aderência ao itinerário (docs/regras-de-negocio.md §4.4, §5.1) —
 * <b>lógica única</b> compartilhada pela regra {@code OUT_OF_ROUTE} e pela máquina de
 * desvio, resolvendo o débito de unificação (§10). Resolve os shapes da linha pela
 * {@link RouteGeometrySource} (cache, sem I/O) e devolve dentro/fora <b>e a menor
 * distância</b> ao traçado.
 *
 * <p>Um ponto está "dentro" se cair no corredor de <b>qualquer</b> shape: distância
 * {@code ≤ corridorMeters} para traçados com ≥ 2 pontos, ou {@code ≤ fallbackMeters}
 * para traçado degenerado (ponto único). Sem geometria ⇒ {@code UNRESOLVED}.
 */
public final class RouteAdherenceEvaluator {

    private static final int MIN_POINTS_FOR_CORRIDOR = 2;

    private final RouteGeometrySource geometry;
    private final double corridorMeters;
    private final double fallbackMeters;

    public RouteAdherenceEvaluator(RouteGeometrySource geometry, double corridorMeters, double fallbackMeters) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.corridorMeters = corridorMeters;
        this.fallbackMeters = fallbackMeters;
    }

    public RouteAdherence evaluate(String serviceCode, Coordinates coordinates) {
        if (serviceCode == null || serviceCode.isBlank() || coordinates.isZeroZero()) {
            return RouteAdherence.unresolved();
        }
        List<RoutePath> paths = geometry.pathsFor(serviceCode);
        if (paths.isEmpty()) {
            return RouteAdherence.unresolved();
        }
        double best = Double.MAX_VALUE;
        boolean inside = false;
        for (RoutePath path : paths) {
            double distance = path.distanceMeters(coordinates);
            if (distance < best) {
                best = distance;
            }
            double threshold = path.points().size() >= MIN_POINTS_FOR_CORRIDOR ? corridorMeters : fallbackMeters;
            if (distance <= threshold) {
                inside = true;
            }
        }
        return inside ? RouteAdherence.inside(best) : RouteAdherence.outside(best);
    }
}

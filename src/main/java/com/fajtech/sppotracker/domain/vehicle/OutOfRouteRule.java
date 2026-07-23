package com.fajtech.sppotracker.domain.vehicle;

import com.fajtech.sppotracker.domain.route.RouteGeometrySource;
import com.fajtech.sppotracker.domain.route.RoutePath;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Fora de rota (docs/regras-de-negocio.md §4.4): a posição está fora do corredor de
 * <b>todos</b> os shapes resolvidos da linha.
 *
 * <ul>
 *   <li>Resolve os shapes pela linha ({@code serviceCode}) via {@link RouteGeometrySource}
 *       — geometria já em cache, sem I/O no hot path.</li>
 *   <li>"Na rota" se cair no corredor (±{@code corridorMeters}) de <b>qualquer</b> shape.</li>
 *   <li>{@code OUT_OF_ROUTE} só quando há geometria e o ponto está fora de todos.</li>
 *   <li>Sem geometria (não resolvida / linha inexistente / sem shapes) ⇒ sem tag:
 *       a regra fica efetivamente neutra, nunca derrubando a classificação.</li>
 *   <li>Traçado degenerado (ponto único, sem corredor) usa o limiar de distância de
 *       fallback ({@code fallbackMeters}, default 100 m).</li>
 * </ul>
 */
public final class OutOfRouteRule implements ClassificationRule {

    private static final int MIN_POINTS_FOR_CORRIDOR = 2;

    private final RouteGeometrySource geometry;
    private final double corridorMeters;
    private final double fallbackMeters;

    public OutOfRouteRule(RouteGeometrySource geometry, double corridorMeters, double fallbackMeters) {
        this.geometry = Objects.requireNonNull(geometry, "geometry");
        this.corridorMeters = corridorMeters;
        this.fallbackMeters = fallbackMeters;
    }

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        String serviceCode = position.serviceCode();
        if (serviceCode == null || serviceCode.isBlank()) {
            return Set.of();
        }
        Coordinates coordinates = position.coordinates();
        if (coordinates.isZeroZero()) {
            return Set.of();
        }
        List<RoutePath> paths = geometry.pathsFor(serviceCode);
        if (paths.isEmpty()) {
            return Set.of();
        }
        for (RoutePath path : paths) {
            double threshold = path.points().size() >= MIN_POINTS_FOR_CORRIDOR ? corridorMeters : fallbackMeters;
            if (path.withinCorridor(coordinates, threshold)) {
                return Set.of();
            }
        }
        return Set.of(ClassificationTag.OUT_OF_ROUTE);
    }
}

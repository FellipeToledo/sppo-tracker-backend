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
 *
 * <p>Antes de resolver, filtra códigos que não podem ser linha
 * ({@link LineCodeKey#looksLikeLine}) — é o único ponto do sistema que dispara resolução
 * de geometria, então a guarda vale para a classificação (§4.4) e para a máquina de
 * desvio (§5) de uma vez.
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
        // Só pede geometria para código que pode ser linha: o campo `linha` do feed também
        // carrega estado operacional (GARAGEM, ESPECIAL, 1 GAR…), que nunca terá shape.
        // Ver LineCodeKey#looksLikeLine — evita I/O inútil e mantém a métrica de resolução
        // significando "linha de verdade sem planejamento".
        if (!LineCodeKey.looksLikeLine(serviceCode) || coordinates.isZeroZero()) {
            return RouteAdherence.pending();
        }
        List<RoutePath> paths = geometry.pathsFor(serviceCode);
        if (paths.isEmpty()) {
            // Separa o persistente do transitório: sem isso, "veículos sem itinerário"
            // conta cache frio junto com linha fora do planejamento.
            return geometry.availabilityFor(serviceCode) == RouteGeometrySource.Availability.NOT_PLANNED
                    ? RouteAdherence.notPlanned()
                    : RouteAdherence.pending();
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

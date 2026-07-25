package com.fajtech.sppotracker.domain.vehicle;

/**
 * Tags produzidas pelas regras de classificação (docs/regras-de-negocio.md §4.3).
 * As regras devolvem tags; o {@link PositionClassifier} decide o status final por
 * precedência fixa. Uma tag pode ficar registrada mesmo sem definir o status
 * (ex.: {@code OUT_OF_ROUTE} preservada quando {@code STALE} vence).
 */
public enum ClassificationTag {
    INVALID_COORDINATES,
    OUT_OF_MUNICIPALITY,
    GARAGE_GEOFENCE,
    IN_GARAGE,
    SUSPICIOUS_SERVICE_CODE,
    STALE,
    OUT_OF_ROUTE,
    /**
     * Aderência ao itinerário <b>não avaliada</b>: o código é de linha, mas não há
     * geometria para ele (linha sem planejamento no feed, ou ainda não resolvida).
     * <b>Não define status</b> — é rótulo, para que "não verificado" não se confunda com
     * "verificado e dentro da rota". Ver §4.4.
     */
    ROUTE_UNRESOLVED
}

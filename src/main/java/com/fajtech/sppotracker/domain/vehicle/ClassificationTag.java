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
     * A linha <b>não tem itinerário no feed vigente</b> (resposta autoritativa da fonte de
     * geometria). Estado <b>persistente</b>: linha suspensa, sazonal, ou que a SMTR ainda
     * não publicou. <b>Não define status</b> — é rótulo, para que "não verificado" não se
     * confunda com "verificado e dentro da rota". Ver §4.4.
     */
    ROUTE_NOT_PLANNED,
    /**
     * Itinerário <b>ainda não resolvido</b> (cache frio, resolução em background). Estado
     * <b>transitório</b> — some no próximo ciclo. Separado de {@link #ROUTE_NOT_PLANNED}
     * porque juntos inflariam o número operacional a cada reinício. Não define status.
     */
    ROUTE_PENDING
}

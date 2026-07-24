package com.fajtech.sppotracker.domain.route;

import java.time.Instant;
import java.util.Objects;

/**
 * Evento de desvio de itinerário emitido pela máquina de estados
 * (docs/regras-de-negocio.md §5). Value object imutável; publicado em tempo real e
 * persistido no histórico (§7.4).
 *
 * @param vehicleId       ordem do veículo
 * @param serviceCode     linha (identificador usado para resolver os shapes)
 * @param routeId         rota do payload, quando disponível (nula com o feed público)
 * @param type            tipo do evento (ALERT/CONFIRMED/RETURN/CANCELLED)
 * @param severity        severidade pela distância máxima do episódio
 * @param distanceMeters  distância relevante no momento (máx. do episódio até então)
 * @param occurredAt      instante do evento (UTC)
 */
public record RouteDeviationEvent(
        String vehicleId,
        String serviceCode,
        String routeId,
        DeviationEventType type,
        DeviationSeverity severity,
        double distanceMeters,
        Instant occurredAt) {

    public RouteDeviationEvent {
        Objects.requireNonNull(vehicleId, "vehicleId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}

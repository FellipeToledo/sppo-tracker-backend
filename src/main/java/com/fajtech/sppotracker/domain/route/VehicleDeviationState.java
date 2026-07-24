package com.fajtech.sppotracker.domain.route;

import java.time.Instant;

/**
 * Estado imutável da máquina de desvio de um veículo (docs/regras-de-negocio.md §5.3).
 * Guardado em cache por veículo (com TTL); cada observação produz um novo estado.
 *
 * @param phase              fase atual (ON_ROUTE / OFF_ROUTE)
 * @param consecutiveOutside pontos consecutivos "efetivamente fora"
 * @param consecutiveInside  pontos consecutivos dentro
 * @param alertEmitted       já emitiu ALERT no episódio corrente
 * @param confirmed          já emitiu CONFIRMED no episódio corrente
 * @param episodeStart       instante de abertura do episódio (null em ON_ROUTE)
 * @param maxDistanceMeters  distância máxima do episódio/corrida fora
 * @param lastObservedAt     instante da última observação processada
 * @param serviceCode        linha do episódio (para compor o evento no sweep)
 * @param routeId            rota do episódio, quando disponível
 */
public record VehicleDeviationState(
        DeviationPhase phase,
        int consecutiveOutside,
        int consecutiveInside,
        boolean alertEmitted,
        boolean confirmed,
        Instant episodeStart,
        double maxDistanceMeters,
        Instant lastObservedAt,
        String serviceCode,
        String routeId) {

    public static VehicleDeviationState initial() {
        return new VehicleDeviationState(
                DeviationPhase.ON_ROUTE, 0, 0, false, false, null, 0.0, null, null, null);
    }

    public boolean episodeOpen() {
        return phase == DeviationPhase.OFF_ROUTE;
    }
}

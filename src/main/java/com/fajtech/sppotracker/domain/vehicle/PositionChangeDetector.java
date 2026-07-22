package com.fajtech.sppotracker.domain.vehicle;

import java.util.Objects;

/**
 * Regra de domínio pura: decide se uma posição candidata representa uma
 * <b>mudança</b> em relação ao snapshot atual do veículo
 * (docs/regras-de-negocio.md §3.3).
 *
 * <p>Considera-se mudança quando:
 * <ol>
 *   <li>não há snapshot anterior (primeira vez), <b>ou</b></li>
 *   <li>a candidata é <b>mais nova</b> ({@code positionTimestamp} estritamente
 *       posterior) <b>e</b> difere em pelo menos um: coordenadas, velocidade,
 *       heading ou contexto de rota ({@code serviceCode}, {@code directionCode},
 *       {@code routeId}, {@code tripId}, {@code shapeId}).</li>
 * </ol>
 * Se a candidata não é mais nova que o snapshot, é ignorada (não mudou).
 */
public class PositionChangeDetector {

    public boolean hasChanged(VehiclePosition candidate, VehiclePosition previous) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        if (previous == null) {
            return true;
        }
        if (!candidate.positionTimestamp().isAfter(previous.positionTimestamp())) {
            return false;
        }
        return !Objects.equals(candidate.coordinates(), previous.coordinates())
                || !Objects.equals(candidate.speed(), previous.speed())
                || !Objects.equals(candidate.heading(), previous.heading())
                || !Objects.equals(candidate.serviceCode(), previous.serviceCode())
                || !Objects.equals(candidate.directionCode(), previous.directionCode())
                || !Objects.equals(candidate.routeId(), previous.routeId())
                || !Objects.equals(candidate.tripId(), previous.tripId())
                || !Objects.equals(candidate.shapeId(), previous.shapeId());
    }
}

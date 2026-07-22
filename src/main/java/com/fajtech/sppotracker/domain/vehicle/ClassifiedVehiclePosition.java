package com.fajtech.sppotracker.domain.vehicle;

import java.util.Objects;

/**
 * Posição já classificada: o dado que passa a ser guardado no snapshot atual e,
 * futuramente, publicado (docs/regras-de-negocio.md §3, §4).
 */
public record ClassifiedVehiclePosition(VehiclePosition position, PositionClassification classification) {

    public ClassifiedVehiclePosition {
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(classification, "classification");
    }
}

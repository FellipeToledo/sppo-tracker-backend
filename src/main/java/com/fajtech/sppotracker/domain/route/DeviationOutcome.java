package com.fajtech.sppotracker.domain.route;

import java.util.List;

/**
 * Saída de um passo da máquina de desvio (docs/regras-de-negocio.md §5.3): o novo
 * estado e os eventos emitidos nesse passo (0..N).
 */
public record DeviationOutcome(VehicleDeviationState state, List<RouteDeviationEvent> events) {

    public DeviationOutcome {
        events = events == null ? List.of() : List.copyOf(events);
    }
}

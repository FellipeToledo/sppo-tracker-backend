package com.fajtech.sppotracker.domain.vehicle;

import java.time.Instant;
import java.util.Set;

/**
 * Coordenadas inválidas: posição exatamente em (0,0) (docs/regras-de-negocio.md
 * §4.3). Reusa {@link Coordinates#isZeroZero()}.
 */
public class InvalidCoordinatesRule implements ClassificationRule {

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        return position.coordinates().isZeroZero()
                ? Set.of(ClassificationTag.INVALID_COORDINATES)
                : Set.of();
    }
}

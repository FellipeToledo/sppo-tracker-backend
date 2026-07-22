package com.fajtech.sppotracker.domain.vehicle;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Fora do município: posição fora do bounding box do Rio
 * (docs/regras-de-negocio.md §4.3).
 */
public class OutOfMunicipalityRule implements ClassificationRule {

    private final BoundingBox municipality;

    public OutOfMunicipalityRule(BoundingBox municipality) {
        this.municipality = Objects.requireNonNull(municipality, "municipality");
    }

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        return municipality.contains(position.coordinates())
                ? Set.of()
                : Set.of(ClassificationTag.OUT_OF_MUNICIPALITY);
    }
}

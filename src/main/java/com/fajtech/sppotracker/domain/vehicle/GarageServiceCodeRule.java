package com.fajtech.sppotracker.domain.vehicle;

import java.time.Instant;
import java.util.Set;

/**
 * Garagem por código de serviço: {@code serviceCode} normalizado pertence ao
 * conjunto de garagem (docs/regras-de-negocio.md §4.3). Produz a tag
 * {@code IN_GARAGE} (também atingível por geofence, em fatia posterior).
 */
public class GarageServiceCodeRule implements ClassificationRule {

    private static final Set<String> GARAGE_CODES = Set.of("GARAGEM", "1 GAR");

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        String normalized = ServiceCodeNormalizer.normalize(position.serviceCode());
        return GARAGE_CODES.contains(normalized)
                ? Set.of(ClassificationTag.IN_GARAGE)
                : Set.of();
    }
}

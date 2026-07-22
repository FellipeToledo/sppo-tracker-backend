package com.fajtech.sppotracker.domain.vehicle;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Classificador de posições (docs/regras-de-negocio.md §4). Coleta as tags de
 * todas as {@link ClassificationRule} e decide o status final por
 * <b>precedência fixa</b> (§4.2) — não pela ordem das regras:
 *
 * <pre>
 * INVALID_COORDINATES → OUT_OF_MUNICIPALITY → (IN_GARAGE | GARAGE_GEOFENCE)
 *   → SUSPICIOUS_SERVICE_CODE → STALE → OUT_OF_ROUTE → (senão) IN_OPERATION
 * </pre>
 *
 * As tags coletadas são preservadas na {@link PositionClassification} mesmo quando
 * não definem o status (ex.: {@code OUT_OF_ROUTE} sob {@code STALE}).
 */
public class PositionClassifier {

    private final List<ClassificationRule> rules;

    public PositionClassifier(List<ClassificationRule> rules) {
        this.rules = List.copyOf(Objects.requireNonNull(rules, "rules"));
    }

    public PositionClassification classify(VehiclePosition position, Instant now) {
        Set<ClassificationTag> tags = EnumSet.noneOf(ClassificationTag.class);
        for (ClassificationRule rule : rules) {
            tags.addAll(rule.evaluate(position, now));
        }
        return PositionClassification.from(resolveStatus(tags), tags);
    }

    private static VehiclePositionStatus resolveStatus(Set<ClassificationTag> tags) {
        if (tags.contains(ClassificationTag.INVALID_COORDINATES)) {
            return VehiclePositionStatus.INVALID;
        }
        if (tags.contains(ClassificationTag.OUT_OF_MUNICIPALITY)) {
            return VehiclePositionStatus.OUT_OF_MUNICIPALITY;
        }
        if (tags.contains(ClassificationTag.IN_GARAGE) || tags.contains(ClassificationTag.GARAGE_GEOFENCE)) {
            return VehiclePositionStatus.IN_GARAGE;
        }
        if (tags.contains(ClassificationTag.SUSPICIOUS_SERVICE_CODE)) {
            return VehiclePositionStatus.SUSPICIOUS;
        }
        if (tags.contains(ClassificationTag.STALE)) {
            return VehiclePositionStatus.STALE;
        }
        if (tags.contains(ClassificationTag.OUT_OF_ROUTE)) {
            return VehiclePositionStatus.OUT_OF_ROUTE;
        }
        return VehiclePositionStatus.IN_OPERATION;
    }
}

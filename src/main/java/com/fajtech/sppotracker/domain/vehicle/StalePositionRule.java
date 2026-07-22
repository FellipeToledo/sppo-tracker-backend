package com.fajtech.sppotracker.domain.vehicle;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Posição obsoleta (stale): {@code positionTimestamp + threshold < now}
 * (docs/regras-de-negocio.md §4.3). No limite exato ({@code == now}) não dispara.
 */
public class StalePositionRule implements ClassificationRule {

    private final Duration threshold;

    public StalePositionRule(Duration threshold) {
        this.threshold = Objects.requireNonNull(threshold, "threshold");
    }

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        return position.positionTimestamp().plus(threshold).isBefore(now)
                ? Set.of(ClassificationTag.STALE)
                : Set.of();
    }
}

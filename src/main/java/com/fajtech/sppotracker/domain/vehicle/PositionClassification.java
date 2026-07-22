package com.fajtech.sppotracker.domain.vehicle;

import java.util.EnumSet;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Resultado da classificação de uma posição (docs/regras-de-negocio.md §4): o
 * {@code status} final, o conjunto de {@code tags} coletadas pelas regras e os
 * flags derivados para consumo/filtragem.
 *
 * <p>Os flags seguem a tabela §4.5 e são derivados de {@code status}+{@code tags}
 * pela factory {@link #from(VehiclePositionStatus, Set)} — o único ponto onde
 * `STALE` depende da presença da tag {@code OUT_OF_ROUTE} para definir `onRoute`.
 */
public record PositionClassification(
        VehiclePositionStatus status,
        Set<ClassificationTag> tags,
        boolean valid,
        boolean insideMunicipality,
        boolean insideGarage,
        boolean onRoute) {

    public PositionClassification {
        Objects.requireNonNull(status, "status");
        tags = tags == null ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(normalize(tags)));
    }

    private static Set<ClassificationTag> normalize(Set<ClassificationTag> tags) {
        return tags.isEmpty() ? EnumSet.noneOf(ClassificationTag.class) : tags;
    }

    /** Deriva os flags (§4.5) a partir do status e das tags. */
    public static PositionClassification from(VehiclePositionStatus status, Set<ClassificationTag> tags) {
        Objects.requireNonNull(status, "status");
        Set<ClassificationTag> safeTags = tags == null ? Set.of() : tags;
        return switch (status) {
            case INVALID -> new PositionClassification(status, safeTags, false, false, false, false);
            case OUT_OF_MUNICIPALITY -> new PositionClassification(status, safeTags, true, false, false, true);
            case IN_GARAGE -> new PositionClassification(status, safeTags, true, true, true, false);
            case SUSPICIOUS -> new PositionClassification(status, safeTags, true, true, false, false);
            case STALE -> new PositionClassification(status, safeTags, true, true, false,
                    !safeTags.contains(ClassificationTag.OUT_OF_ROUTE));
            case OUT_OF_ROUTE -> new PositionClassification(status, safeTags, true, true, false, false);
            case IN_OPERATION -> new PositionClassification(status, safeTags, true, true, false, true);
        };
    }
}

package com.fajtech.sppotracker.domain.vehicle;

import java.time.Instant;
import java.util.Set;

/**
 * Regra de classificação (docs/regras-de-negocio.md §4.3, §9): dado uma posição e
 * o instante de avaliação, devolve zero ou mais {@link ClassificationTag}. Regras
 * são independentes entre si; a precedência é decidida no {@link PositionClassifier},
 * não pela ordem das regras. Nova regra = novo componente que devolve tag(s).
 */
public interface ClassificationRule {

    Set<ClassificationTag> evaluate(VehiclePosition position, Instant now);
}

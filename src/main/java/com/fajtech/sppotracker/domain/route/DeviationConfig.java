package com.fajtech.sppotracker.domain.route;

import java.time.Duration;
import java.util.Objects;

/**
 * Parâmetros da máquina de estados de desvio (docs/regras-de-negocio.md §5, §8).
 * Value object puro; a borda mapeia a configuração externa para ele.
 *
 * @param confirmationMarginMeters margem de "efetivamente fora" (§5.2, default 30 m)
 * @param alertPoints              pontos consecutivos fora p/ ALERT/C1 (default 3)
 * @param confirmSustained         tempo sustentado p/ CONFIRMED/C2 (default 3 min)
 * @param confirmDistanceMeters    distância p/ CONFIRMED imediato (default 150 m)
 * @param returnPoints             pontos consecutivos dentro p/ RETURN/CANCELLED (default 3)
 * @param severityMedioMeters      limite superior de LEVE (§5.4, default 150 m)
 * @param severityGraveMeters      limite superior de MEDIO (§5.4, default 500 m)
 */
public record DeviationConfig(
        double confirmationMarginMeters,
        int alertPoints,
        Duration confirmSustained,
        double confirmDistanceMeters,
        int returnPoints,
        double severityMedioMeters,
        double severityGraveMeters) {

    public DeviationConfig {
        Objects.requireNonNull(confirmSustained, "confirmSustained");
        if (alertPoints < 1 || returnPoints < 1) {
            throw new IllegalArgumentException("alertPoints and returnPoints must be >= 1");
        }
    }
}

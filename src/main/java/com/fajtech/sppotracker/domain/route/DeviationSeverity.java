package com.fajtech.sppotracker.domain.route;

/**
 * Severidade do episódio de desvio pela distância máxima (docs/regras-de-negocio.md §5.4):
 * {@code LEVE ≤ medioThreshold < MEDIO ≤ graveThreshold < GRAVE}.
 */
public enum DeviationSeverity {
    LEVE,
    MEDIO,
    GRAVE;

    /**
     * Classifica a severidade pela distância máxima do episódio.
     *
     * @param maxDistanceMeters distância máxima observada, em metros
     * @param medioThreshold    limite superior de {@code LEVE} (default 150 m)
     * @param graveThreshold    limite superior de {@code MEDIO} (default 500 m)
     */
    public static DeviationSeverity fromMaxDistance(double maxDistanceMeters,
                                                    double medioThreshold, double graveThreshold) {
        if (maxDistanceMeters <= medioThreshold) {
            return LEVE;
        }
        if (maxDistanceMeters <= graveThreshold) {
            return MEDIO;
        }
        return GRAVE;
    }
}

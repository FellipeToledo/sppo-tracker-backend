package com.fajtech.sppotracker.domain.route;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviationSeverityTest {

    private static final double MEDIO = 150.0;
    private static final double GRAVE = 500.0;

    @Test
    void shouldClassifyLeveUpToMedioThreshold() {
        assertThat(DeviationSeverity.fromMaxDistance(0, MEDIO, GRAVE)).isEqualTo(DeviationSeverity.LEVE);
        assertThat(DeviationSeverity.fromMaxDistance(150, MEDIO, GRAVE)).isEqualTo(DeviationSeverity.LEVE);
    }

    @Test
    void shouldClassifyMedioBetweenThresholds() {
        assertThat(DeviationSeverity.fromMaxDistance(150.1, MEDIO, GRAVE)).isEqualTo(DeviationSeverity.MEDIO);
        assertThat(DeviationSeverity.fromMaxDistance(500, MEDIO, GRAVE)).isEqualTo(DeviationSeverity.MEDIO);
    }

    @Test
    void shouldClassifyGraveAboveGraveThreshold() {
        assertThat(DeviationSeverity.fromMaxDistance(500.1, MEDIO, GRAVE)).isEqualTo(DeviationSeverity.GRAVE);
    }
}

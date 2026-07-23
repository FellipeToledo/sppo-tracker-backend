package com.fajtech.sppotracker.domain.route;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contrato de normalização compartilhado com o {@code sppo-gtfs-service}. Os casos
 * espelham o {@code LineCode} do serviço para garantir que as chaves batam.
 */
class LineCodeKeyTest {

    @Test
    void shouldTrimAndUppercase() {
        assertThat(LineCodeKey.of("  sv789 ")).isEqualTo("SV789");
    }

    @Test
    void shouldStripLeadingZerosWhenNumeric() {
        assertThat(LineCodeKey.of("0100")).isEqualTo("100");
        assertThat(LineCodeKey.of("100")).isEqualTo("100");
    }

    @Test
    void shouldKeepLeadingZerosWhenNotPurelyNumeric() {
        assertThat(LineCodeKey.of("0A1")).isEqualTo("0A1");
    }

    @Test
    void shouldKeepAtLeastOneDigitForAllZeros() {
        assertThat(LineCodeKey.of("000")).isEqualTo("0");
    }

    @Test
    void shouldReturnNullForBlankOrNull() {
        assertThat(LineCodeKey.of("   ")).isNull();
        assertThat(LineCodeKey.of(null)).isNull();
    }
}

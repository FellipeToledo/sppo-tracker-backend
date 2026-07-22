package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parsing defensivo de timestamps epoch: tolera segundos ou milissegundos
 * (heurística: valor &lt; 1e11 → segundos) (§1).
 */
class EpochParserTest {

    @Test
    void shouldParseMilliseconds() {
        // 1690000000000 ms = 2023-07-22T05:46:40Z
        assertThat(EpochParser.parse("1690000000000")).isEqualTo(Instant.ofEpochMilli(1690000000000L));
    }

    @Test
    void shouldParseSeconds() {
        // 1690000000 s (< 1e11) = 2023-07-22T05:46:40Z
        assertThat(EpochParser.parse("1690000000")).isEqualTo(Instant.ofEpochSecond(1690000000L));
    }

    @Test
    void shouldTreatValueBelowThresholdAsSeconds() {
        // 99999999999 < 1e11 → segundos
        assertThat(EpochParser.parse("99999999999")).isEqualTo(Instant.ofEpochSecond(99999999999L));
    }

    @Test
    void shouldTreatThresholdAsMilliseconds() {
        // 100000000000 == 1e11 → milissegundos
        assertThat(EpochParser.parse("100000000000")).isEqualTo(Instant.ofEpochMilli(100000000000L));
    }

    @Test
    void shouldTrimSurroundingWhitespace() {
        assertThat(EpochParser.parse("  1690000000000  ")).isEqualTo(Instant.ofEpochMilli(1690000000000L));
    }

    @Test
    void shouldReturnNullForNullBlankOrInvalid() {
        assertThat(EpochParser.parse(null)).isNull();
        assertThat(EpochParser.parse("")).isNull();
        assertThat(EpochParser.parse("   ")).isNull();
        assertThat(EpochParser.parse("abc")).isNull();
        assertThat(EpochParser.parse("12.5")).isNull();
    }
}

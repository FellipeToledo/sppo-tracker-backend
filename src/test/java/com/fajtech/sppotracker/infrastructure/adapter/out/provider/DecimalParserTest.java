package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Parsing defensivo de coordenadas: aceita vírgula e ponto decimal (§1). */
class DecimalParserTest {

    @Test
    void shouldParseCommaDecimal() {
        assertThat(DecimalParser.parse("-22,89206")).isEqualByComparingTo(new BigDecimal("-22.89206"));
    }

    @Test
    void shouldParseDotDecimal() {
        assertThat(DecimalParser.parse("-22.89206")).isEqualByComparingTo(new BigDecimal("-22.89206"));
    }

    @Test
    void shouldParsePositiveAndZero() {
        assertThat(DecimalParser.parse("43,17")).isEqualByComparingTo(new BigDecimal("43.17"));
        assertThat(DecimalParser.parse("0")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void shouldTrimSurroundingWhitespace() {
        assertThat(DecimalParser.parse("  -22,9  ")).isEqualByComparingTo(new BigDecimal("-22.9"));
    }

    @Test
    void shouldReturnNullForNullBlankOrInvalid() {
        assertThat(DecimalParser.parse(null)).isNull();
        assertThat(DecimalParser.parse("")).isNull();
        assertThat(DecimalParser.parse("   ")).isNull();
        assertThat(DecimalParser.parse("abc")).isNull();
        assertThat(DecimalParser.parse("--1")).isNull();
    }
}

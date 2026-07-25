package com.fajtech.sppotracker.domain.route;

import org.junit.jupiter.api.Test;

import java.util.Locale;

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

    @Test
    void shouldAcceptEveryRealSppoLineFormat() {
        // Formatos observados no feed real (2026-07-25): numérico, SV, SN, LECD, SP, SVB, SR.
        assertThat(LineCodeKey.looksLikeLine("100")).isTrue();
        assertThat(LineCodeKey.looksLikeLine("0100")).isTrue();
        assertThat(LineCodeKey.looksLikeLine("sv789")).isTrue();
        assertThat(LineCodeKey.looksLikeLine("SN810")).isTrue();
        assertThat(LineCodeKey.looksLikeLine("LECD11")).isTrue();
        assertThat(LineCodeKey.looksLikeLine("SVB35")).isTrue();
        assertThat(LineCodeKey.looksLikeLine("2802SV")).isTrue();
    }

    @Test
    void shouldRejectOperationalStatusCodesThatWillNeverHaveAShape() {
        // Todos observados no campo `linha` do feed real — não são linha, são estado.
        assertThat(LineCodeKey.looksLikeLine("GARAGEM")).isFalse();
        assertThat(LineCodeKey.looksLikeLine("ESPECIAL")).isFalse();
        assertThat(LineCodeKey.looksLikeLine("MANUTENCAO")).isFalse();
        assertThat(LineCodeKey.looksLikeLine("TREINO")).isFalse();
        assertThat(LineCodeKey.looksLikeLine("RESERVADO")).isFalse();
        assertThat(LineCodeKey.looksLikeLine("OPER.")).isFalse();
        assertThat(LineCodeKey.looksLikeLine("FORA DE OP")).isFalse();
        assertThat(LineCodeKey.looksLikeLine("1 GAR")).isFalse();
        assertThat(LineCodeKey.looksLikeLine("00000")).isFalse();
    }

    @Test
    void shouldRejectBlankOrNullAsLine() {
        assertThat(LineCodeKey.looksLikeLine("   ")).isFalse();
        assertThat(LineCodeKey.looksLikeLine(null)).isFalse();
    }

    @Test
    void shouldUppercaseIndependentlyOfDefaultLocale() {
        // Locale.ROOT evita o "dotless i" turco: "si" não deve virar "Sİ".
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(new Locale("tr", "TR"));
            assertThat(LineCodeKey.of("si42")).isEqualTo("SI42");
        } finally {
            Locale.setDefault(previous);
        }
    }
}

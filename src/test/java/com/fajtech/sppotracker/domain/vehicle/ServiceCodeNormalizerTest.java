package com.fajtech.sppotracker.domain.vehicle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Normalização do serviceCode para as regras de código (docs/regras-de-negocio.md §4.3). */
class ServiceCodeNormalizerTest {

    @Test
    void shouldUpperCaseTrimAndCollapseSpaces() {
        assertThat(ServiceCodeNormalizer.normalize("  fora   de  op  ")).isEqualTo("FORA DE OP");
    }

    @Test
    void shouldRemoveAccents() {
        assertThat(ServiceCodeNormalizer.normalize("manutenção")).isEqualTo("MANUTENCAO");
        assertThat(ServiceCodeNormalizer.normalize("Operação")).isEqualTo("OPERACAO");
    }

    @Test
    void shouldReturnEmptyForNullOrBlank() {
        assertThat(ServiceCodeNormalizer.normalize(null)).isEmpty();
        assertThat(ServiceCodeNormalizer.normalize("   ")).isEmpty();
    }
}

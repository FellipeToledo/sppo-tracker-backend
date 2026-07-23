package com.fajtech.sppotracker.domain.operator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes do value object de empresa (docs/regras-de-negocio.md §6). */
class CompanyTest {

    @Test
    void shouldNormalizePrefixToUpperCase() {
        assertThat(new Company("a410", "Real Auto Onibus Ltda").prefix()).isEqualTo("A410");
    }

    @Test
    void shouldExposeConsortiumCode() {
        assertThat(new Company("A410", "Real Auto Onibus Ltda").consortiumCode()).isEqualTo("A");
    }

    @Test
    void shouldTrimName() {
        assertThat(new Company("A410", "  Real Auto Onibus Ltda  ").name()).isEqualTo("Real Auto Onibus Ltda");
    }

    @Test
    void shouldRejectPrefixNotFourCharacters() {
        assertThatThrownBy(() -> new Company("A41", "Real Auto Onibus Ltda"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 4");
        assertThatThrownBy(() -> new Company("A4100", "Real Auto Onibus Ltda"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new Company("A410", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.fajtech.sppotracker.domain.operator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes do value object de consórcio (docs/regras-de-negocio.md §6). */
class ConsortiumTest {

    @Test
    void shouldNormalizeCodeToUpperCase() {
        assertThat(new Consortium("a", "Consórcio Intersul").code()).isEqualTo("A");
    }

    @Test
    void shouldTrimName() {
        assertThat(new Consortium("A", "  Consórcio Intersul  ").name()).isEqualTo("Consórcio Intersul");
    }

    @Test
    void shouldRejectMultiCharacterCode() {
        assertThatThrownBy(() -> new Consortium("AB", "Consórcio Intersul"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly 1");
    }

    @Test
    void shouldRejectBlankCode() {
        assertThatThrownBy(() -> new Consortium("  ", "Consórcio Intersul"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new Consortium("A", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

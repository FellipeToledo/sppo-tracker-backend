package com.fajtech.sppotracker.domain.operator;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Invariantes do value object de consórcio (docs/regras-de-negocio.md §6). */
class OperatorTest {

    @Test
    void shouldNormalizePrefixToUpperCase() {
        assertThat(new Operator("a", "Consórcio Intersul").prefix()).isEqualTo("A");
    }

    @Test
    void shouldTrimName() {
        assertThat(new Operator("A", "  Consórcio Intersul  ").name()).isEqualTo("Consórcio Intersul");
    }

    @Test
    void shouldRejectMultiCharacterPrefix() {
        assertThatThrownBy(() -> new Operator("A26I", "Consórcio Intersul"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single character");
    }

    @Test
    void shouldRejectBlankPrefix() {
        assertThatThrownBy(() -> new Operator("  ", "Consórcio Intersul"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankName() {
        assertThatThrownBy(() -> new Operator("A", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

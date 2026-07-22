package com.fajtech.sppotracker.domain.vehicle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Teste-semente: estabelece o padrão de teste do domínio (JUnit 5 + AssertJ). */
class CoordinatesTest {

    @Test
    void shouldRejectOutOfRangeLatitude() {
        assertThatThrownBy(() -> new Coordinates(new BigDecimal("-91"), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldDetectZeroZero() {
        assertThat(new Coordinates(BigDecimal.ZERO, BigDecimal.ZERO).isZeroZero()).isTrue();
        assertThat(new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2")).isZeroZero()).isFalse();
    }
}

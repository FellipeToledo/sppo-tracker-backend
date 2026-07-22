package com.fajtech.sppotracker.domain.vehicle;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Derivação dos flags a partir do status e tags (docs/regras-de-negocio.md §4.5). */
class PositionClassificationTest {

    @Test
    void inOperationFlags() {
        PositionClassification c = PositionClassification.from(VehiclePositionStatus.IN_OPERATION, Set.of());
        assertThat(c.valid()).isTrue();
        assertThat(c.insideMunicipality()).isTrue();
        assertThat(c.insideGarage()).isFalse();
        assertThat(c.onRoute()).isTrue();
    }

    @Test
    void invalidFlags() {
        PositionClassification c = PositionClassification.from(
                VehiclePositionStatus.INVALID, Set.of(ClassificationTag.INVALID_COORDINATES));
        assertThat(c.valid()).isFalse();
        assertThat(c.insideMunicipality()).isFalse();
        assertThat(c.insideGarage()).isFalse();
        assertThat(c.onRoute()).isFalse();
    }

    @Test
    void outOfMunicipalityFlags() {
        PositionClassification c = PositionClassification.from(
                VehiclePositionStatus.OUT_OF_MUNICIPALITY, Set.of(ClassificationTag.OUT_OF_MUNICIPALITY));
        assertThat(c.valid()).isTrue();
        assertThat(c.insideMunicipality()).isFalse();
        assertThat(c.insideGarage()).isFalse();
        assertThat(c.onRoute()).isTrue();
    }

    @Test
    void inGarageFlags() {
        PositionClassification c = PositionClassification.from(
                VehiclePositionStatus.IN_GARAGE, Set.of(ClassificationTag.IN_GARAGE));
        assertThat(c.valid()).isTrue();
        assertThat(c.insideMunicipality()).isTrue();
        assertThat(c.insideGarage()).isTrue();
        assertThat(c.onRoute()).isFalse();
    }

    @Test
    void suspiciousFlags() {
        PositionClassification c = PositionClassification.from(
                VehiclePositionStatus.SUSPICIOUS, Set.of(ClassificationTag.SUSPICIOUS_SERVICE_CODE));
        assertThat(c.valid()).isTrue();
        assertThat(c.insideMunicipality()).isTrue();
        assertThat(c.insideGarage()).isFalse();
        assertThat(c.onRoute()).isFalse();
    }

    @Test
    void staleOnRouteWhenNoOutOfRouteTag() {
        PositionClassification c = PositionClassification.from(
                VehiclePositionStatus.STALE, Set.of(ClassificationTag.STALE));
        assertThat(c.valid()).isTrue();
        assertThat(c.insideMunicipality()).isTrue();
        assertThat(c.insideGarage()).isFalse();
        assertThat(c.onRoute()).isTrue();
    }

    @Test
    void staleOffRouteWhenOutOfRouteTagPresent() {
        PositionClassification c = PositionClassification.from(
                VehiclePositionStatus.STALE,
                Set.of(ClassificationTag.STALE, ClassificationTag.OUT_OF_ROUTE));
        assertThat(c.onRoute()).isFalse();
    }

    @Test
    void outOfRouteFlags() {
        PositionClassification c = PositionClassification.from(
                VehiclePositionStatus.OUT_OF_ROUTE, Set.of(ClassificationTag.OUT_OF_ROUTE));
        assertThat(c.valid()).isTrue();
        assertThat(c.insideMunicipality()).isTrue();
        assertThat(c.insideGarage()).isFalse();
        assertThat(c.onRoute()).isFalse();
    }

    @Test
    void tagsAreImmutable() {
        PositionClassification c = PositionClassification.from(
                VehiclePositionStatus.IN_OPERATION, Set.of(ClassificationTag.STALE));
        assertThat(c.tags()).containsExactly(ClassificationTag.STALE);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> c.tags().add(ClassificationTag.STALE))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}

package com.fajtech.sppotracker.domain.vehicle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Precedência fixa e composição de tags do classificador
 * (docs/regras-de-negocio.md §4.2). Usa regras-fake para isolar a lógica de
 * precedência das regras concretas.
 */
class PositionClassifierTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    private static ClassificationRule ruleReturning(ClassificationTag... tags) {
        Set<ClassificationTag> result = Set.of(tags);
        return (position, now) -> result;
    }

    private static PositionClassifier classifierWith(ClassificationTag... tags) {
        return new PositionClassifier(List.of(ruleReturning(tags)));
    }

    private static VehiclePosition anyPosition() {
        return VehiclePosition.builder()
                .vehicleId("A1")
                .coordinates(new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2")))
                .positionTimestamp(NOW)
                .sentTimestamp(NOW)
                .receivedAt(NOW)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    private VehiclePositionStatus statusFor(ClassificationTag... tags) {
        return classifierWith(tags).classify(anyPosition(), NOW).status();
    }

    @Test
    void noTagsIsInOperation() {
        assertThat(statusFor()).isEqualTo(VehiclePositionStatus.IN_OPERATION);
    }

    @Test
    void invalidWinsOverEverything() {
        assertThat(statusFor(ClassificationTag.INVALID_COORDINATES, ClassificationTag.OUT_OF_MUNICIPALITY,
                ClassificationTag.IN_GARAGE, ClassificationTag.SUSPICIOUS_SERVICE_CODE,
                ClassificationTag.STALE, ClassificationTag.OUT_OF_ROUTE))
                .isEqualTo(VehiclePositionStatus.INVALID);
    }

    @Test
    void outOfMunicipalityWinsOverGarageAndBelow() {
        assertThat(statusFor(ClassificationTag.OUT_OF_MUNICIPALITY, ClassificationTag.IN_GARAGE,
                ClassificationTag.SUSPICIOUS_SERVICE_CODE, ClassificationTag.STALE,
                ClassificationTag.OUT_OF_ROUTE))
                .isEqualTo(VehiclePositionStatus.OUT_OF_MUNICIPALITY);
    }

    @Test
    void inGarageFromServiceCodeOrGeofence() {
        assertThat(statusFor(ClassificationTag.IN_GARAGE, ClassificationTag.SUSPICIOUS_SERVICE_CODE,
                ClassificationTag.STALE)).isEqualTo(VehiclePositionStatus.IN_GARAGE);
        assertThat(statusFor(ClassificationTag.GARAGE_GEOFENCE, ClassificationTag.SUSPICIOUS_SERVICE_CODE))
                .isEqualTo(VehiclePositionStatus.IN_GARAGE);
    }

    @Test
    void suspiciousWinsOverStaleAndOutOfRoute() {
        assertThat(statusFor(ClassificationTag.SUSPICIOUS_SERVICE_CODE, ClassificationTag.STALE,
                ClassificationTag.OUT_OF_ROUTE)).isEqualTo(VehiclePositionStatus.SUSPICIOUS);
    }

    @Test
    void staleWinsOverOutOfRoute() {
        assertThat(statusFor(ClassificationTag.STALE, ClassificationTag.OUT_OF_ROUTE))
                .isEqualTo(VehiclePositionStatus.STALE);
    }

    @Test
    void outOfRouteWhenOnlyTag() {
        assertThat(statusFor(ClassificationTag.OUT_OF_ROUTE)).isEqualTo(VehiclePositionStatus.OUT_OF_ROUTE);
    }

    @Test
    void collectsTagsFromAllRulesAndPreservesThem() {
        PositionClassifier classifier = new PositionClassifier(List.of(
                ruleReturning(ClassificationTag.STALE),
                ruleReturning(ClassificationTag.OUT_OF_ROUTE)));

        PositionClassification result = classifier.classify(anyPosition(), NOW);

        assertThat(result.status()).isEqualTo(VehiclePositionStatus.STALE);
        assertThat(result.tags())
                .containsExactlyInAnyOrder(ClassificationTag.STALE, ClassificationTag.OUT_OF_ROUTE);
        // STALE + tag OUT_OF_ROUTE → onRoute falso (§4.5)
        assertThat(result.onRoute()).isFalse();
    }
}

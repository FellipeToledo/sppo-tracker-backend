package com.fajtech.sppotracker.domain.vehicle;

import com.fajtech.sppotracker.domain.geofence.GarageArea;
import com.fajtech.sppotracker.domain.geofence.GarageLocator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GarageGeofenceRuleTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private static Coordinates at(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    private static GarageArea garage(String id, double lat, double lon) {
        return GarageArea.of(id, "Garagem " + id, List.of(
                at(lat, lon),
                at(lat, lon + 0.01),
                at(lat + 0.01, lon + 0.01),
                at(lat + 0.01, lon)));
    }

    private static GarageGeofenceRule ruleWith(GarageArea... areas) {
        return new GarageGeofenceRule(new GarageLocator(List.of(areas)));
    }

    private static VehiclePosition positionAt(String serviceCode, Coordinates coordinates) {
        return VehiclePosition.builder()
                .vehicleId("A1001")
                .serviceCode(serviceCode)
                .coordinates(coordinates)
                .positionTimestamp(NOW)
                .sentTimestamp(NOW)
                .receivedAt(NOW)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    @Test
    void shouldTagWhenPositionIsInsideAGarage() {
        GarageGeofenceRule rule = ruleWith(garage("g1", -22.90, -43.20));

        var tags = rule.evaluate(positionAt("100", at(-22.895, -43.195)), NOW);

        assertThat(tags).containsExactly(ClassificationTag.GARAGE_GEOFENCE);
    }

    @Test
    void shouldTagRegardlessOfServiceCode() {
        // O valor da regra: veículo com código de serviço operacional, mas fisicamente
        // dentro do pátio, também é IN_GARAGE (§4.3).
        GarageGeofenceRule rule = ruleWith(garage("g1", -22.90, -43.20));

        assertThat(rule.evaluate(positionAt("483", at(-22.895, -43.195)), NOW))
                .containsExactly(ClassificationTag.GARAGE_GEOFENCE);
    }

    @Test
    void shouldNotTagWhenPositionIsOutsideEveryGarage() {
        GarageGeofenceRule rule = ruleWith(
                garage("g1", -22.90, -43.20),
                garage("g2", -22.85, -43.30));

        var tags = rule.evaluate(positionAt("100", at(-22.92, -43.25)), NOW);

        assertThat(tags).isEmpty();
    }

    @Test
    void shouldMatchAnyGarageInTheList() {
        GarageGeofenceRule rule = ruleWith(
                garage("g1", -22.90, -43.20),
                garage("g2", -22.85, -43.30));

        assertThat(rule.evaluate(positionAt("100", at(-22.845, -43.295)), NOW))
                .containsExactly(ClassificationTag.GARAGE_GEOFENCE);
    }

    @Test
    void shouldStayNeutralWithoutAnyGarageLoaded() {
        GarageGeofenceRule rule = ruleWith();

        assertThat(rule.evaluate(positionAt("100", at(-22.895, -43.195)), NOW)).isEmpty();
    }

    @Test
    void shouldMakeAnOperationalVehicleInsideTheYardBeClassifiedInGarage() {
        // Integração das regras reais (§4.2/§4.5): é o ganho da fatia — o ônibus recolhido
        // que segue transmitindo linha operacional passa a ser IN_GARAGE.
        PositionClassifier classifier = new PositionClassifier(List.<ClassificationRule>of(
                new InvalidCoordinatesRule(),
                new OutOfMunicipalityRule(new BoundingBox(
                        BigDecimal.valueOf(-23.10), BigDecimal.valueOf(-22.70),
                        BigDecimal.valueOf(-43.80), BigDecimal.valueOf(-43.05))),
                new GarageServiceCodeRule(),
                ruleWith(garage("g1", -22.90, -43.20)),
                new SuspiciousServiceCodeRule(),
                new StalePositionRule(Duration.ofMinutes(5))));

        PositionClassification classification =
                classifier.classify(positionAt("483", at(-22.895, -43.195)), NOW);

        assertThat(classification.status()).isEqualTo(VehiclePositionStatus.IN_GARAGE);
        assertThat(classification.tags()).contains(ClassificationTag.GARAGE_GEOFENCE);
        assertThat(classification.insideGarage()).isTrue();
        assertThat(classification.onRoute()).isFalse();
    }

    @Test
    void shouldNotTagInvalidZeroZeroPosition() {
        // (0,0) é lixo do feed: nunca deve virar garagem. A INVALID vence de todo
        // jeito na precedência (§4.2), mas a regra não deve nem produzir a tag.
        GarageGeofenceRule rule = ruleWith(
                GarageArea.of("g0", "Ilha nula", List.of(
                        at(-0.01, -0.01), at(-0.01, 0.01), at(0.01, 0.01), at(0.01, -0.01))));

        var tags = rule.evaluate(positionAt("100", at(0.0, 0.0)), NOW);

        assertThat(tags).isEmpty();
    }
}

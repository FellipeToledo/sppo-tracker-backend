package com.fajtech.sppotracker.domain.vehicle;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** Regras concretas de classificação (docs/regras-de-negocio.md §4.3). */
class ClassificationRulesTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    private static VehiclePosition position(Coordinates coords, String serviceCode, Instant positionTs) {
        return VehiclePosition.builder()
                .vehicleId("A12345")
                .serviceCode(serviceCode)
                .coordinates(coords)
                .positionTimestamp(positionTs)
                .sentTimestamp(positionTs)
                .receivedAt(positionTs)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    private static Coordinates at(String lat, String lon) {
        return new Coordinates(new BigDecimal(lat), new BigDecimal(lon));
    }

    private static final Coordinates RIO = at("-22.90", "-43.20");

    // --- InvalidCoordinatesRule ---

    @Test
    void invalidCoordinatesFiresAtZeroZero() {
        ClassificationRule rule = new InvalidCoordinatesRule();
        assertThat(rule.evaluate(position(at("0", "0"), "100", NOW), NOW))
                .containsExactly(ClassificationTag.INVALID_COORDINATES);
        assertThat(rule.evaluate(position(RIO, "100", NOW), NOW)).isEmpty();
    }

    // --- OutOfMunicipalityRule ---

    @Test
    void outOfMunicipalityFiresOutsideBox() {
        BoundingBox box = new BoundingBox(new BigDecimal("-23.10"), new BigDecimal("-22.70"),
                new BigDecimal("-43.80"), new BigDecimal("-43.05"));
        ClassificationRule rule = new OutOfMunicipalityRule(box);
        assertThat(rule.evaluate(position(at("-22.50", "-43.20"), "100", NOW), NOW))
                .containsExactly(ClassificationTag.OUT_OF_MUNICIPALITY);
        assertThat(rule.evaluate(position(RIO, "100", NOW), NOW)).isEmpty();
    }

    // --- GarageServiceCodeRule ---

    @Test
    void garageServiceCodeFiresForGarageCodes() {
        ClassificationRule rule = new GarageServiceCodeRule();
        assertThat(rule.evaluate(position(RIO, "garagem", NOW), NOW))
                .containsExactly(ClassificationTag.IN_GARAGE);
        assertThat(rule.evaluate(position(RIO, "1 GAR", NOW), NOW))
                .containsExactly(ClassificationTag.IN_GARAGE);
        assertThat(rule.evaluate(position(RIO, "100", NOW), NOW)).isEmpty();
    }

    // --- SuspiciousServiceCodeRule ---

    @Test
    void suspiciousServiceCodeFiresForSuspiciousCodes() {
        ClassificationRule rule = new SuspiciousServiceCodeRule();
        // com acento e espaços — normalização deve casar
        assertThat(rule.evaluate(position(RIO, " Manutenção ", NOW), NOW))
                .containsExactly(ClassificationTag.SUSPICIOUS_SERVICE_CODE);
        assertThat(rule.evaluate(position(RIO, "FORA DE OPERACAO", NOW), NOW))
                .containsExactly(ClassificationTag.SUSPICIOUS_SERVICE_CODE);
        assertThat(rule.evaluate(position(RIO, "00000", NOW), NOW))
                .containsExactly(ClassificationTag.SUSPICIOUS_SERVICE_CODE);
        assertThat(rule.evaluate(position(RIO, "100", NOW), NOW)).isEmpty();
    }

    // --- StalePositionRule ---

    @Test
    void staleFiresWhenOlderThanThreshold() {
        ClassificationRule rule = new StalePositionRule(Duration.ofMinutes(5));
        Instant sixMinAgo = NOW.minus(Duration.ofMinutes(6));
        Instant fourMinAgo = NOW.minus(Duration.ofMinutes(4));

        assertThat(rule.evaluate(position(RIO, "100", sixMinAgo), NOW))
                .containsExactly(ClassificationTag.STALE);
        assertThat(rule.evaluate(position(RIO, "100", fourMinAgo), NOW)).isEmpty();
    }

    @Test
    void staleDoesNotFireExactlyAtThreshold() {
        ClassificationRule rule = new StalePositionRule(Duration.ofMinutes(5));
        Instant fiveMinAgo = NOW.minus(Duration.ofMinutes(5));
        // positionTimestamp + threshold == now → não é "< now"
        assertThat(rule.evaluate(position(RIO, "100", fiveMinAgo), NOW)).isEmpty();
    }
}

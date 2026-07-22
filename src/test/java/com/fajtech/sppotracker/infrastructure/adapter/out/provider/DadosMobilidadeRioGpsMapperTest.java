package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Mapeamento defensivo do item cru do feed para VehiclePosition (§1, §2, §9). */
class DadosMobilidadeRioGpsMapperTest {

    private static final Instant FIXED_NOW = Instant.parse("2026-07-22T12:00:00Z");
    private final DadosMobilidadeRioGpsMapper mapper =
            new DadosMobilidadeRioGpsMapper(Clock.fixed(FIXED_NOW, ZoneOffset.UTC));

    private static DadosMobilidadeRioGpsItem item(String ordem, String lat, String lon,
                                                  String datahora, String datahoraenvio) {
        return new DadosMobilidadeRioGpsItem(ordem, lat, lon, datahora, "23,5", "100",
                datahoraenvio, "1690000005000");
    }

    @Test
    void shouldMapValidItem() {
        DadosMobilidadeRioGpsItem raw = item("A12345", "-22,89206", "-43,17654",
                "1690000000000", "1690000002000");

        Optional<VehiclePosition> mapped = mapper.map(raw);

        assertThat(mapped).isPresent();
        VehiclePosition p = mapped.get();
        assertThat(p.vehicleId()).isEqualTo("A12345");
        assertThat(p.serviceCode()).isEqualTo("100");
        assertThat(p.coordinates().latitude()).isEqualByComparingTo(new BigDecimal("-22.89206"));
        assertThat(p.coordinates().longitude()).isEqualByComparingTo(new BigDecimal("-43.17654"));
        assertThat(p.speed()).isEqualTo(23.5);
        assertThat(p.positionTimestamp()).isEqualTo(Instant.ofEpochMilli(1690000000000L));
        assertThat(p.sentTimestamp()).isEqualTo(Instant.ofEpochMilli(1690000002000L));
        assertThat(p.serverTimestamp()).isEqualTo(Instant.ofEpochMilli(1690000005000L));
        assertThat(p.receivedAt()).isEqualTo(FIXED_NOW);
        assertThat(p.source()).isEqualTo(PositionSource.DADOS_MOBILIDADE_RIO);
        // Campos ausentes no feed público:
        assertThat(p.directionCode()).isNull();
        assertThat(p.routeId()).isNull();
        assertThat(p.tripId()).isNull();
        assertThat(p.shapeId()).isNull();
        assertThat(p.heading()).isNull();
    }

    @Test
    void shouldDiscardItemWithBlankVehicleId() {
        assertThat(mapper.map(item("  ", "-22,9", "-43,1", "1690000000000", "1690000002000"))).isEmpty();
    }

    @Test
    void shouldDiscardItemWithInvalidCoordinates() {
        assertThat(mapper.map(item("A1", "abc", "-43,1", "1690000000000", "1690000002000"))).isEmpty();
    }

    @Test
    void shouldDiscardItemWithOutOfRangeCoordinates() {
        assertThat(mapper.map(item("A1", "-999", "-43,1", "1690000000000", "1690000002000"))).isEmpty();
    }

    @Test
    void shouldDiscardItemWithMissingPositionTimestamp() {
        assertThat(mapper.map(item("A1", "-22,9", "-43,1", "", "1690000002000"))).isEmpty();
    }

    @Test
    void shouldDiscardItemWithMissingSentTimestamp() {
        assertThat(mapper.map(item("A1", "-22,9", "-43,1", "1690000000000", null))).isEmpty();
    }

    @Test
    void shouldKeepGoodItemsWhenBatchHasOneBadItem() {
        List<DadosMobilidadeRioGpsItem> batch = List.of(
                item("A1", "-22,9", "-43,1", "1690000000000", "1690000002000"),
                item("A2", "abc", "-43,1", "1690000000000", "1690000002000"), // malformado
                item("A3", "-22,8", "-43,2", "1690000000000", "1690000002000"));

        List<VehiclePosition> result = mapper.mapAll(batch);

        assertThat(result).extracting(VehiclePosition::vehicleId).containsExactly("A1", "A3");
    }

    @Test
    void shouldNormalizeNegativeSpeedToNull() {
        DadosMobilidadeRioGpsItem raw = new DadosMobilidadeRioGpsItem(
                "A1", "-22,9", "-43,1", "1690000000000", "-5", "100", "1690000002000", null);
        assertThat(mapper.map(raw).orElseThrow().speed()).isNull();
    }
}

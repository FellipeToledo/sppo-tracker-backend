package com.fajtech.sppotracker.application.geofence;

import com.fajtech.sppotracker.domain.geofence.GarageArea;
import com.fajtech.sppotracker.domain.geofence.GarageLocator;
import com.fajtech.sppotracker.domain.vehicle.ClassificationTag;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Consulta de garagens e rótulo exposto na borda (§4.3, §7.1). */
class GarageServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-25T12:00:00Z");

    private static Coordinates at(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    private static GarageArea garage() {
        return GarageArea.of("g1", "Real Auto Ônibus", "Av. Brasil, 1", "Maré", List.of(
                at(-22.90, -43.20), at(-22.90, -43.19), at(-22.89, -43.19), at(-22.89, -43.20)));
    }

    private static GarageService serviceWith(GarageArea... areas) {
        return new GarageService(new GarageLocator(List.of(areas)));
    }

    private static ClassifiedVehiclePosition classified(Coordinates coordinates,
                                                        VehiclePositionStatus status,
                                                        Set<ClassificationTag> tags) {
        VehiclePosition position = VehiclePosition.builder()
                .vehicleId("A12345")
                .serviceCode("100")
                .coordinates(coordinates)
                .positionTimestamp(NOW)
                .sentTimestamp(NOW)
                .receivedAt(NOW)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
        return new ClassifiedVehiclePosition(position, PositionClassification.from(status, tags));
    }

    @Test
    void shouldListLoadedGarages() {
        assertThat(serviceWith(garage()).garages())
                .singleElement()
                .extracting(GarageArea::name, GarageArea::address, GarageArea::neighborhood)
                .containsExactly("Real Auto Ônibus", "Av. Brasil, 1", "Maré");
    }

    @Test
    void shouldLocateTheGarageContainingThePosition() {
        assertThat(serviceWith(garage()).locate(at(-22.895, -43.195)))
                .get()
                .extracting(GarageArea::id)
                .isEqualTo("g1");
    }

    @Test
    void shouldNotLocateOutsideAnyGarage() {
        assertThat(serviceWith(garage()).locate(at(-22.97, -43.18))).isEmpty();
    }

    @Test
    void shouldLabelGarageNameWhenClassificationCarriesTheGeofenceTag() {
        var classified = classified(at(-22.895, -43.195), VehiclePositionStatus.IN_GARAGE,
                Set.of(ClassificationTag.GARAGE_GEOFENCE));

        assertThat(serviceWith(garage()).garageNameFor(classified)).contains("Real Auto Ônibus");
    }

    @Test
    void shouldNotLabelWhenGarageCameOnlyFromTheServiceCode() {
        // IN_GARAGE por código de serviço não afirma em QUAL pátio o veículo está —
        // e a posição aqui está longe de qualquer polígono.
        var classified = classified(at(-22.97, -43.18), VehiclePositionStatus.IN_GARAGE,
                Set.of(ClassificationTag.IN_GARAGE));

        assertThat(serviceWith(garage()).garageNameFor(classified)).isEmpty();
    }

    @Test
    void shouldNotLabelWithoutAnyGarageLoaded() {
        var classified = classified(at(-22.895, -43.195), VehiclePositionStatus.IN_GARAGE,
                Set.of(ClassificationTag.GARAGE_GEOFENCE));

        assertThat(serviceWith().garageNameFor(classified)).isEmpty();
    }
}

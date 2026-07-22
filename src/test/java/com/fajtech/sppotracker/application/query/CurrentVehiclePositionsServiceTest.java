package com.fajtech.sppotracker.application.query;

import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Filtragem do snapshot atual (docs/regras-de-negocio.md §7.1). */
class CurrentVehiclePositionsServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-22T12:00:00Z");

    private CurrentSnapshotStorePort snapshotStore;
    private CurrentVehiclePositionsService service;

    @BeforeEach
    void setUp() {
        snapshotStore = mock(CurrentSnapshotStorePort.class);
        service = new CurrentVehiclePositionsService(snapshotStore);
    }

    private static ClassifiedVehiclePosition snapshot(String vehicleId, String serviceCode,
                                                      String routeId, VehiclePositionStatus status) {
        VehiclePosition position = VehiclePosition.builder()
                .vehicleId(vehicleId)
                .serviceCode(serviceCode)
                .routeId(routeId)
                .coordinates(new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2")))
                .positionTimestamp(NOW)
                .sentTimestamp(NOW)
                .receivedAt(NOW)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
        return new ClassifiedVehiclePosition(position, PositionClassification.from(status, Set.of()));
    }

    @Test
    void shouldReturnAllWhenUnfiltered() {
        when(snapshotStore.findAll()).thenReturn(List.of(
                snapshot("A1", "100", "R1", VehiclePositionStatus.IN_OPERATION),
                snapshot("A2", "200", "R2", VehiclePositionStatus.IN_GARAGE)));

        List<ClassifiedVehiclePosition> result = service.getCurrent(VehiclePositionFilter.unfiltered());

        assertThat(result).extracting(c -> c.position().vehicleId()).containsExactly("A1", "A2");
    }

    @Test
    void shouldFilterByServiceCode() {
        when(snapshotStore.findAll()).thenReturn(List.of(
                snapshot("A1", "100", "R1", VehiclePositionStatus.IN_OPERATION),
                snapshot("A2", "200", "R2", VehiclePositionStatus.IN_OPERATION)));

        List<ClassifiedVehiclePosition> result = service.getCurrent(
                new VehiclePositionFilter("200", null, null));

        assertThat(result).extracting(c -> c.position().vehicleId()).containsExactly("A2");
    }

    @Test
    void shouldFilterByRouteId() {
        when(snapshotStore.findAll()).thenReturn(List.of(
                snapshot("A1", "100", "R1", VehiclePositionStatus.IN_OPERATION),
                snapshot("A2", "100", "R2", VehiclePositionStatus.IN_OPERATION)));

        List<ClassifiedVehiclePosition> result = service.getCurrent(
                new VehiclePositionFilter(null, "R1", null));

        assertThat(result).extracting(c -> c.position().vehicleId()).containsExactly("A1");
    }

    @Test
    void shouldFilterByClassificationStatus() {
        when(snapshotStore.findAll()).thenReturn(List.of(
                snapshot("A1", "100", "R1", VehiclePositionStatus.IN_OPERATION),
                snapshot("A2", "100", "R1", VehiclePositionStatus.IN_GARAGE)));

        List<ClassifiedVehiclePosition> result = service.getCurrent(
                new VehiclePositionFilter(null, null, VehiclePositionStatus.IN_GARAGE));

        assertThat(result).extracting(c -> c.position().vehicleId()).containsExactly("A2");
    }

    @Test
    void shouldCombineFilters() {
        when(snapshotStore.findAll()).thenReturn(List.of(
                snapshot("A1", "100", "R1", VehiclePositionStatus.IN_OPERATION),
                snapshot("A2", "100", "R1", VehiclePositionStatus.IN_GARAGE),
                snapshot("A3", "200", "R1", VehiclePositionStatus.IN_OPERATION)));

        List<ClassifiedVehiclePosition> result = service.getCurrent(
                new VehiclePositionFilter("100", "R1", VehiclePositionStatus.IN_OPERATION));

        assertThat(result).extracting(c -> c.position().vehicleId()).containsExactly("A1");
    }

    @Test
    void shouldReturnEmptyWhenNoSnapshots() {
        when(snapshotStore.findAll()).thenReturn(List.of());
        assertThat(service.getCurrent(VehiclePositionFilter.unfiltered())).isEmpty();
    }
}

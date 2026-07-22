package com.fajtech.sppotracker.application.query;

import com.fajtech.sppotracker.application.port.in.GetCurrentVehiclePositionsUseCase;
import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;

import java.util.List;
import java.util.Objects;

/**
 * Consulta o snapshot atual da frota e aplica os filtros em memória
 * (docs/regras-de-negocio.md §7.1). Classe pura de aplicação, instanciada pela
 * infraestrutura.
 */
public class CurrentVehiclePositionsService implements GetCurrentVehiclePositionsUseCase {

    private final CurrentSnapshotStorePort snapshotStore;

    public CurrentVehiclePositionsService(CurrentSnapshotStorePort snapshotStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
    }

    @Override
    public List<ClassifiedVehiclePosition> getCurrent(VehiclePositionFilter filter) {
        VehiclePositionFilter effective = filter == null ? VehiclePositionFilter.unfiltered() : filter;
        return snapshotStore.findAll().stream()
                .filter(effective::matches)
                .toList();
    }
}

package com.fajtech.sppotracker.application.query;

import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;

import java.util.Objects;

/**
 * Filtro do snapshot atual (docs/regras-de-negocio.md §7.1). Campos nulos são
 * ignorados (casam com qualquer valor). Comparação exata.
 *
 * @param serviceCode          filtra por {@code serviceCode} da posição
 * @param routeId              filtra por {@code routeId} da posição
 * @param classificationStatus filtra pelo status da classificação
 */
public record VehiclePositionFilter(String serviceCode, String routeId,
                                    VehiclePositionStatus classificationStatus) {

    public static VehiclePositionFilter unfiltered() {
        return new VehiclePositionFilter(null, null, null);
    }

    public boolean matches(ClassifiedVehiclePosition snapshot) {
        return matchesServiceCode(snapshot) && matchesRouteId(snapshot) && matchesStatus(snapshot);
    }

    private boolean matchesServiceCode(ClassifiedVehiclePosition snapshot) {
        return serviceCode == null || Objects.equals(serviceCode, snapshot.position().serviceCode());
    }

    private boolean matchesRouteId(ClassifiedVehiclePosition snapshot) {
        return routeId == null || Objects.equals(routeId, snapshot.position().routeId());
    }

    private boolean matchesStatus(ClassifiedVehiclePosition snapshot) {
        return classificationStatus == null
                || classificationStatus == snapshot.classification().status();
    }
}

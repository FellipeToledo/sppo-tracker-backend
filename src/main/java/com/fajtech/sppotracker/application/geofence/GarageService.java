package com.fajtech.sppotracker.application.geofence;

import com.fajtech.sppotracker.application.port.in.GarageQueryUseCase;
import com.fajtech.sppotracker.domain.geofence.GarageArea;
import com.fajtech.sppotracker.domain.geofence.GarageLocator;
import com.fajtech.sppotracker.domain.vehicle.ClassificationTag;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Caso de uso de consulta das garagens (docs/regras-de-negocio.md §4.3, §7.1).
 * Delega ao {@link GarageLocator} — o dado é estático, carregado no boot, então não há
 * I/O aqui. Existe para que os adapters de entrada (REST, WebSocket) dependam de uma
 * porta e não do domínio direto nem do adapter de saída.
 */
public class GarageService implements GarageQueryUseCase {

    private final GarageLocator locator;

    public GarageService(GarageLocator locator) {
        this.locator = Objects.requireNonNull(locator, "locator");
    }

    @Override
    public List<GarageArea> garages() {
        return locator.garages();
    }

    @Override
    public Optional<GarageArea> locate(Coordinates position) {
        return locator.locate(position);
    }

    @Override
    public Optional<String> garageNameFor(ClassifiedVehiclePosition classified) {
        if (classified == null
                || !classified.classification().tags().contains(ClassificationTag.GARAGE_GEOFENCE)) {
            return Optional.empty();
        }
        return locate(classified.position().coordinates()).map(GarageArea::name);
    }
}

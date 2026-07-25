package com.fajtech.sppotracker.application.port.in;

import com.fajtech.sppotracker.domain.geofence.GarageArea;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;

import java.util.List;
import java.util.Optional;

/**
 * Consulta das geofences de garagem (docs/regras-de-negocio.md §4.3, §7.1): lista as
 * áreas carregadas e localiza a garagem de uma coordenada. Lista vazia é resposta
 * legítima (geofence desligada ou dataset sem polígonos).
 */
public interface GarageQueryUseCase {

    List<GarageArea> garages();

    Optional<GarageArea> locate(Coordinates position);

    /**
     * Nome da garagem a rotular na posição (borda REST/WebSocket, §7.1/§7.2).
     * Só responde quando a classificação carrega a tag {@code GARAGE_GEOFENCE} —
     * {@code IN_GARAGE} vindo apenas do código de serviço não afirma nada sobre
     * <i>qual</i> pátio, e evita varrer os polígonos para toda a frota.
     */
    Optional<String> garageNameFor(ClassifiedVehiclePosition classified);
}

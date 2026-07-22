package com.fajtech.sppotracker.application.port.in;

import com.fajtech.sppotracker.application.query.VehiclePositionFilter;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;

import java.util.List;

/**
 * Porta de entrada: consulta o snapshot atual da frota, aplicando filtros
 * (docs/regras-de-negocio.md §7.1).
 */
public interface GetCurrentVehiclePositionsUseCase {

    List<ClassifiedVehiclePosition> getCurrent(VehiclePositionFilter filter);
}

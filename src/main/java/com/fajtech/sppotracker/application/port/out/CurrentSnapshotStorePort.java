package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;

import java.util.Optional;

/**
 * Porta de saída do snapshot atual por veículo — a camada de tempo real
 * (docs/regras-de-negocio.md §3.4). Um snapshot por veículo, renovado a cada
 * heartbeat, com TTL. Quando o veículo para de transmitir, o snapshot expira e
 * sai do "current".
 */
public interface CurrentSnapshotStorePort {

    /** Snapshot atual do veículo, se existir. */
    Optional<VehiclePosition> find(String vehicleId);

    /** Salva/renova o snapshot atual do veículo (com TTL). */
    void save(VehiclePosition position);
}

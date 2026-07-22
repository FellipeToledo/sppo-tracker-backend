package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;

import java.util.List;
import java.util.Optional;

/**
 * Porta de saída do snapshot atual por veículo — a camada de tempo real
 * (docs/regras-de-negocio.md §3.4). Guarda a posição já classificada (status +
 * flags + tags). Um snapshot por veículo, renovado a cada heartbeat, com TTL.
 * Quando o veículo para de transmitir, o snapshot expira e sai do "current".
 */
public interface CurrentSnapshotStorePort {

    /** Snapshot atual do veículo, se existir. */
    Optional<ClassifiedVehiclePosition> find(String vehicleId);

    /** Salva/renova o snapshot atual do veículo (com TTL). */
    void save(ClassifiedVehiclePosition snapshot);

    /** Todos os snapshots atuais (veículos que ainda estão no "current"). */
    List<ClassifiedVehiclePosition> findAll();
}

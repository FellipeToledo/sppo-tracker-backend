package com.fajtech.sppotracker.infrastructure.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repositório do histórico de eventos de desvio (docs/regras-de-negocio.md §7.4).
 * Esqueleto: a escrita entra com a máquina de estados de desvio (§5).
 */
public interface RouteDeviationEventRepository extends JpaRepository<RouteDeviationEventEntity, Long> {

    List<RouteDeviationEventEntity> findByVehicleIdOrderByOccurredAtDesc(String vehicleId);
}

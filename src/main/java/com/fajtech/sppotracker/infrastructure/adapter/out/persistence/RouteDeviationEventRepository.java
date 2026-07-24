package com.fajtech.sppotracker.infrastructure.adapter.out.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositório do histórico de eventos de desvio (docs/regras-de-negocio.md §7.4).
 * Escrita pela máquina de desvio (§5); leitura pelo endpoint de histórico (§7.1).
 */
public interface RouteDeviationEventRepository extends JpaRepository<RouteDeviationEventEntity, Long> {

    List<RouteDeviationEventEntity> findByVehicleIdOrderByOccurredAtDesc(String vehicleId);

    /**
     * Busca por filtros opcionais (nulos = sem filtro), do mais recente para o mais
     * antigo. {@code eventType}/{@code severity} são comparados pelo nome do enum.
     */
    @Query("""
            select e from RouteDeviationEventEntity e
            where (:vehicleId is null or e.vehicleId = :vehicleId)
              and (:serviceCode is null or e.serviceCode = :serviceCode)
              and (:eventType is null or e.eventType = :eventType)
              and (:severity is null or e.severity = :severity)
            order by e.occurredAt desc, e.id desc
            """)
    List<RouteDeviationEventEntity> search(@Param("vehicleId") String vehicleId,
                                           @Param("serviceCode") String serviceCode,
                                           @Param("eventType") String eventType,
                                           @Param("severity") String severity,
                                           Pageable pageable);
}

package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.application.ingestion.IngestionResult;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;

import java.time.Duration;

/**
 * Porta de saída para métricas do hot path (docs/regras-de-negocio.md §7.3). Mantém
 * a aplicação livre de framework; a implementação (Micrometer) vive na infraestrutura.
 */
public interface IngestionMetricsPort {

    /** Contadores do lote processado (received/duplicated/unchanged/changed). */
    void recordBatch(IngestionResult result);

    /** Uma classificação decidida, contabilizada por status. */
    void recordClassification(VehiclePositionStatus status);

    /** Idade da posição no momento da ingestão (now − positionTimestamp). */
    void recordPositionAge(Duration age);
}

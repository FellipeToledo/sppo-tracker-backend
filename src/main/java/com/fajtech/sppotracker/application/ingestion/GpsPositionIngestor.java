package com.fajtech.sppotracker.application.ingestion;

import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.application.port.out.DeduplicationPort;
import com.fajtech.sppotracker.domain.vehicle.PositionChangeDetector;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;

import java.util.List;
import java.util.Objects;

/**
 * Processa um lote de posições no hot path (docs/regras-de-negocio.md §3), por
 * posição e na ordem: deduplicação → detecção de mudança vs. snapshot atual →
 * salvar snapshot. Classificação, publicação e métricas entram em fatias
 * posteriores.
 *
 * <p>Classe pura de aplicação (sem framework), instanciada pela infraestrutura.
 */
public class GpsPositionIngestor {

    private final DeduplicationPort deduplication;
    private final PositionChangeDetector changeDetector;
    private final CurrentSnapshotStorePort snapshotStore;

    public GpsPositionIngestor(DeduplicationPort deduplication,
                               PositionChangeDetector changeDetector,
                               CurrentSnapshotStorePort snapshotStore) {
        this.deduplication = Objects.requireNonNull(deduplication, "deduplication");
        this.changeDetector = Objects.requireNonNull(changeDetector, "changeDetector");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
    }

    public IngestionResult ingest(List<VehiclePosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return IngestionResult.empty();
        }
        int duplicated = 0;
        int unchanged = 0;
        int changed = 0;
        for (VehiclePosition position : positions) {
            if (deduplication.isDuplicate(position)) {
                duplicated++;
                continue;
            }
            VehiclePosition previous = snapshotStore.find(position.vehicleId()).orElse(null);
            if (!changeDetector.hasChanged(position, previous)) {
                unchanged++;
                continue;
            }
            snapshotStore.save(position);
            changed++;
        }
        return new IngestionResult(positions.size(), duplicated, unchanged, changed);
    }
}

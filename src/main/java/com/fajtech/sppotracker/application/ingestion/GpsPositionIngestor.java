package com.fajtech.sppotracker.application.ingestion;

import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.application.port.out.DeduplicationPort;
import com.fajtech.sppotracker.application.port.out.PublishVehiclePositionEventPort;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.PositionChangeDetector;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.PositionClassifier;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Processa um lote de posições no hot path (docs/regras-de-negocio.md §3), por
 * posição e na ordem: deduplicação → detecção de mudança vs. snapshot atual →
 * classificação → salvar snapshot → publicar evento. Métricas entram em fatia
 * posterior.
 *
 * <p>Classe pura de aplicação (sem framework), instanciada pela infraestrutura.
 */
public class GpsPositionIngestor {

    private final DeduplicationPort deduplication;
    private final PositionChangeDetector changeDetector;
    private final PositionClassifier classifier;
    private final CurrentSnapshotStorePort snapshotStore;
    private final PublishVehiclePositionEventPort eventPublisher;
    private final Clock clock;

    public GpsPositionIngestor(DeduplicationPort deduplication,
                               PositionChangeDetector changeDetector,
                               PositionClassifier classifier,
                               CurrentSnapshotStorePort snapshotStore,
                               PublishVehiclePositionEventPort eventPublisher,
                               Clock clock) {
        this.deduplication = Objects.requireNonNull(deduplication, "deduplication");
        this.changeDetector = Objects.requireNonNull(changeDetector, "changeDetector");
        this.classifier = Objects.requireNonNull(classifier, "classifier");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public IngestionResult ingest(List<VehiclePosition> positions) {
        if (positions == null || positions.isEmpty()) {
            return IngestionResult.empty();
        }
        Instant now = clock.instant();
        int duplicated = 0;
        int unchanged = 0;
        int changed = 0;
        for (VehiclePosition position : positions) {
            if (deduplication.isDuplicate(position)) {
                duplicated++;
                continue;
            }
            VehiclePosition previous = snapshotStore.find(position.vehicleId())
                    .map(ClassifiedVehiclePosition::position)
                    .orElse(null);
            if (!changeDetector.hasChanged(position, previous)) {
                unchanged++;
                continue;
            }
            PositionClassification classification = classifier.classify(position, now);
            ClassifiedVehiclePosition classified = new ClassifiedVehiclePosition(position, classification);
            snapshotStore.save(classified);
            eventPublisher.publish(classified);
            changed++;
        }
        return new IngestionResult(positions.size(), duplicated, unchanged, changed);
    }
}

package com.fajtech.sppotracker.application.ingestion;

import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.application.port.out.DeduplicationPort;
import com.fajtech.sppotracker.application.port.out.PublishVehiclePositionEventPort;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionChangeDetector;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.PositionClassifier;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pipeline por posição: dedup → detecção de mudança → classificação → snapshot (§3). */
class GpsPositionIngestorTest {

    private static final Instant T1 = Instant.parse("2026-07-22T12:00:00Z");
    private static final Instant T2 = Instant.parse("2026-07-22T12:00:20Z");

    private DeduplicationPort deduplication;
    private CurrentSnapshotStorePort snapshotStore;
    private PublishVehiclePositionEventPort eventPublisher;
    private GpsPositionIngestor ingestor;

    @BeforeEach
    void setUp() {
        deduplication = mock(DeduplicationPort.class);
        snapshotStore = mock(CurrentSnapshotStorePort.class);
        eventPublisher = mock(PublishVehiclePositionEventPort.class);
        // classifier sem regras → IN_OPERATION; a classificação tem testes próprios
        PositionClassifier classifier = new PositionClassifier(List.of());
        ingestor = new GpsPositionIngestor(deduplication, new PositionChangeDetector(), classifier,
                snapshotStore, eventPublisher, Clock.fixed(T2, ZoneOffset.UTC));
        when(deduplication.isDuplicate(any())).thenReturn(false);
        when(snapshotStore.find(any())).thenReturn(Optional.empty());
    }

    private static VehiclePosition position(String vehicleId, Instant positionTs, String lat) {
        return VehiclePosition.builder()
                .vehicleId(vehicleId)
                .serviceCode("100")
                .coordinates(new Coordinates(new BigDecimal(lat), new BigDecimal("-43.2")))
                .speed(30.0)
                .positionTimestamp(positionTs)
                .sentTimestamp(positionTs)
                .receivedAt(positionTs)
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    private static ClassifiedVehiclePosition classified(VehiclePosition position) {
        return new ClassifiedVehiclePosition(position,
                PositionClassification.from(VehiclePositionStatus.IN_OPERATION, Set.of()));
    }

    private List<VehiclePosition> savedPositions() {
        ArgumentCaptor<ClassifiedVehiclePosition> captor =
                ArgumentCaptor.forClass(ClassifiedVehiclePosition.class);
        verify(snapshotStore, org.mockito.Mockito.atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream().map(ClassifiedVehiclePosition::position).toList();
    }

    private List<VehiclePosition> publishedPositions() {
        ArgumentCaptor<ClassifiedVehiclePosition> captor =
                ArgumentCaptor.forClass(ClassifiedVehiclePosition.class);
        verify(eventPublisher, org.mockito.Mockito.atLeast(0)).publish(captor.capture());
        return captor.getAllValues().stream().map(ClassifiedVehiclePosition::position).toList();
    }

    @Test
    void shouldSaveNewChangedPositions() {
        VehiclePosition p1 = position("A1", T1, "-22.90");
        VehiclePosition p2 = position("A2", T1, "-22.80");

        IngestionResult result = ingestor.ingest(List.of(p1, p2));

        assertThat(result).isEqualTo(new IngestionResult(2, 0, 0, 2));
        assertThat(savedPositions()).containsExactly(p1, p2);
        assertThat(publishedPositions()).containsExactly(p1, p2);
    }

    @Test
    void shouldSkipDuplicatesWithoutSaving() {
        VehiclePosition p = position("A1", T1, "-22.90");
        when(deduplication.isDuplicate(p)).thenReturn(true);

        IngestionResult result = ingestor.ingest(List.of(p));

        assertThat(result).isEqualTo(new IngestionResult(1, 1, 0, 0));
        verify(snapshotStore, never()).find(any());
        verify(snapshotStore, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldSkipUnchangedWithoutSaving() {
        VehiclePosition previous = position("A1", T2, "-22.90");
        VehiclePosition candidate = position("A1", T1, "-22.80"); // mais antigo → não mudou
        when(snapshotStore.find("A1")).thenReturn(Optional.of(classified(previous)));

        IngestionResult result = ingestor.ingest(List.of(candidate));

        assertThat(result).isEqualTo(new IngestionResult(1, 0, 1, 0));
        verify(snapshotStore, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void shouldSaveWhenChangedVersusPreviousSnapshot() {
        VehiclePosition previous = position("A1", T1, "-22.90");
        VehiclePosition candidate = position("A1", T2, "-22.80"); // mais novo + coords diferentes
        when(snapshotStore.find("A1")).thenReturn(Optional.of(classified(previous)));

        IngestionResult result = ingestor.ingest(List.of(candidate));

        assertThat(result).isEqualTo(new IngestionResult(1, 0, 0, 1));
        assertThat(savedPositions()).containsExactly(candidate);
        assertThat(publishedPositions()).containsExactly(candidate);
    }

    @Test
    void shouldCountMixedBatch() {
        VehiclePosition changed = position("A1", T1, "-22.90");
        VehiclePosition duplicate = position("A2", T1, "-22.80");
        VehiclePosition unchanged = position("A3", T1, "-22.70");
        when(deduplication.isDuplicate(duplicate)).thenReturn(true);
        when(snapshotStore.find("A3")).thenReturn(Optional.of(classified(position("A3", T2, "-22.70"))));

        IngestionResult result = ingestor.ingest(List.of(changed, duplicate, unchanged));

        assertThat(result).isEqualTo(new IngestionResult(3, 1, 1, 1));
        assertThat(savedPositions()).containsExactly(changed);
        assertThat(publishedPositions()).containsExactly(changed);
    }

    @Test
    void shouldReturnEmptyForEmptyBatch() {
        assertThat(ingestor.ingest(List.of())).isEqualTo(IngestionResult.empty());
    }
}

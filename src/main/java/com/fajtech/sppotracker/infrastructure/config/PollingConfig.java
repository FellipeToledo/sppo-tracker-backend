package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.application.ingestion.GpsPositionIngestor;
import com.fajtech.sppotracker.application.polling.GpsPollingService;
import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.application.port.out.DeduplicationPort;
import com.fajtech.sppotracker.application.port.out.FetchExternalGpsPositionsPort;
import com.fajtech.sppotracker.application.port.out.ProviderReadinessPort;
import com.fajtech.sppotracker.domain.vehicle.PositionChangeDetector;
import com.fajtech.sppotracker.domain.vehicle.PositionClassifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring do caso de uso de polling. Mantém a camada de aplicação livre de
 * framework: a infraestrutura instancia o {@link GpsPollingService} a partir das
 * {@link GpsPollingProperties}.
 */
@Configuration
@EnableConfigurationProperties(GpsPollingProperties.class)
public class PollingConfig {

    @Bean
    public PositionChangeDetector positionChangeDetector() {
        return new PositionChangeDetector();
    }

    @Bean
    public GpsPositionIngestor gpsPositionIngestor(DeduplicationPort deduplication,
                                                   PositionChangeDetector changeDetector,
                                                   PositionClassifier classifier,
                                                   CurrentSnapshotStorePort snapshotStore,
                                                   Clock clock) {
        return new GpsPositionIngestor(deduplication, changeDetector, classifier, snapshotStore, clock);
    }

    @Bean
    public GpsPollingService gpsPollingService(FetchExternalGpsPositionsPort fetchPort,
                                               ProviderReadinessPort readinessPort,
                                               GpsPositionIngestor ingestor,
                                               Clock clock,
                                               GpsPollingProperties properties) {
        return new GpsPollingService(
                fetchPort,
                readinessPort,
                ingestor,
                clock,
                properties.overlapWindow(),
                properties.failureCooldownThreshold(),
                properties.failureCooldown());
    }
}

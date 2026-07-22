package com.fajtech.sppotracker.infrastructure.adapter.out.redis;

import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.domain.vehicle.ClassificationTag;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT do snapshot atual contra um Redis real (Testcontainers): serialização JSON
 * (Jackson 3), roundtrip {@code save}/{@code find} e {@code findAll} via SCAN.
 * Roda via {@code mvn verify} (precisa de Docker).
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "gps.polling.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,"
                + "org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration,"
                + "org.springframework.boot.data.jpa.autoconfigure.DataJpaRepositoriesAutoConfiguration"
})
class RedisCurrentSnapshotStoreIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private CurrentSnapshotStorePort snapshotStore;

    private static ClassifiedVehiclePosition snapshot(String vehicleId) {
        VehiclePosition position = VehiclePosition.builder()
                .vehicleId(vehicleId)
                .serviceCode("100")
                .coordinates(new Coordinates(new BigDecimal("-22.89206"), new BigDecimal("-43.17654")))
                .speed(23.5)
                .heading(90)
                .positionTimestamp(Instant.parse("2026-07-22T12:00:00Z"))
                .sentTimestamp(Instant.parse("2026-07-22T12:00:02Z"))
                .serverTimestamp(Instant.parse("2026-07-22T12:00:05Z"))
                .receivedAt(Instant.parse("2026-07-22T12:00:06Z"))
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
        PositionClassification classification = PositionClassification.from(
                VehiclePositionStatus.STALE, Set.of(ClassificationTag.STALE, ClassificationTag.OUT_OF_ROUTE));
        return new ClassifiedVehiclePosition(position, classification);
    }

    @Test
    void savesAndReadsBackViaJson() {
        ClassifiedVehiclePosition snapshot = snapshot("A11111");

        snapshotStore.save(snapshot);

        Optional<ClassifiedVehiclePosition> found = snapshotStore.find("A11111");
        assertThat(found).contains(snapshot);
    }

    @Test
    void findAllScansStoredSnapshots() {
        snapshotStore.save(snapshot("A22222"));
        snapshotStore.save(snapshot("A33333"));

        assertThat(snapshotStore.findAll())
                .extracting(c -> c.position().vehicleId())
                .contains("A22222", "A33333");
    }
}

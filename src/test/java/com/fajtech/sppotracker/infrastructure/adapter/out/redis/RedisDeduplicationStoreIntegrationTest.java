package com.fajtech.sppotracker.infrastructure.adapter.out.redis;

import com.fajtech.sppotracker.application.port.out.DeduplicationPort;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT de deduplicação contra um Redis real (Testcontainers). Exercita a semântica
 * de SET NX EX e a expiração (TTL). Roda via {@code mvn verify} (precisa de Docker).
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
class RedisDeduplicationStoreIntegrationTest {

    @Container
    @ServiceConnection(name = "redis")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private DeduplicationPort deduplication;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static VehiclePosition position() {
        Instant ts = Instant.ofEpochMilli(1690000000000L);
        return VehiclePosition.builder()
                .vehicleId("A12345")
                .coordinates(new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2")))
                .positionTimestamp(ts)
                .sentTimestamp(ts.plusSeconds(2))
                .receivedAt(Instant.now())
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    @Test
    void firstIsNewSecondIsDuplicate() {
        VehiclePosition position = position();

        assertThat(deduplication.isDuplicate(position)).isFalse();
        assertThat(deduplication.isDuplicate(position)).isTrue();
    }

    @Test
    void marksKeyWithTtl() {
        deduplication.isDuplicate(position());

        var keys = redisTemplate.keys("gps:dedup:*");
        assertThat(keys).isNotEmpty();
        String key = keys.iterator().next();
        assertThat(redisTemplate.getExpire(key)).isPositive();
    }
}

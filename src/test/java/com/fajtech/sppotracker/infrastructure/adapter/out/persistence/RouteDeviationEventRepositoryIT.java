package com.fajtech.sppotracker.infrastructure.adapter.out.persistence;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * IT do histórico de eventos de desvio contra um Postgres real (Testcontainers):
 * a migração Flyway V1 roda no boot, e o repositório JPA persiste/consulta.
 * O Redis é mockado (não é o foco). Roda via {@code mvn verify} (precisa de Docker).
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(properties = {
        "gps.polling.enabled=false",
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration,"
                + "org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration"
})
class RouteDeviationEventRepositoryIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    @MockitoBean
    private StringRedisTemplate stringRedisTemplate;

    @MockitoBean
    private RedisConnectionFactory redisConnectionFactory;

    @MockitoBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    @Autowired
    private RouteDeviationEventRepository repository;

    @Test
    void persistsAndQueriesByVehicleWithFlywaySchema() {
        RouteDeviationEventEntity event = new RouteDeviationEventEntity();
        event.setVehicleId("A99999");
        event.setRouteId("R100");
        event.setEventType("CONFIRMED");
        event.setSeverity("MEDIO");
        event.setDistanceMeters(240.0);
        event.setOccurredAt(Instant.parse("2026-07-22T12:00:00Z"));

        RouteDeviationEventEntity saved = repository.save(event);
        assertThat(saved.getId()).isNotNull();

        List<RouteDeviationEventEntity> found = repository.findByVehicleIdOrderByOccurredAtDesc("A99999");
        assertThat(found).hasSize(1);
        RouteDeviationEventEntity row = found.get(0);
        assertThat(row.getEventType()).isEqualTo("CONFIRMED");
        assertThat(row.getSeverity()).isEqualTo("MEDIO");
        assertThat(row.getDistanceMeters()).isEqualTo(240.0);
        // created_at tem default no banco (Flyway) e é preenchido na inserção
        assertThat(row.getCreatedAt()).isNotNull();
    }
}

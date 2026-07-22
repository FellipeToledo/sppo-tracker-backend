package com.fajtech.sppotracker.infrastructure.adapter.out.redis;

import com.fajtech.sppotracker.domain.vehicle.ClassificationTag;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import com.fajtech.sppotracker.infrastructure.config.CurrentSnapshotProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Serialização/desserialização do snapshot sobre um StringRedisTemplate mockado (§3.4). */
class RedisCurrentSnapshotStoreTest {

    private static final Duration TTL = Duration.ofMinutes(15);

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
    private ValueOperations<String, String> valueOps;
    private RedisCurrentSnapshotStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new RedisCurrentSnapshotStore(redisTemplate, objectMapper,
                new CurrentSnapshotProperties(TTL));
    }

    private static ClassifiedVehiclePosition snapshot() {
        VehiclePosition position = VehiclePosition.builder()
                .vehicleId("A12345")
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
                VehiclePositionStatus.STALE,
                Set.of(ClassificationTag.STALE, ClassificationTag.OUT_OF_ROUTE));
        return new ClassifiedVehiclePosition(position, classification);
    }

    @Test
    void shouldSaveSerializedSnapshotWithTtl() throws Exception {
        ClassifiedVehiclePosition snapshot = snapshot();

        store.save(snapshot);

        verify(valueOps).set(eq("gps:snapshot:A12345"),
                eq(objectMapper.writeValueAsString(snapshot)), eq(TTL));
    }

    @Test
    void shouldFindAndDeserializeSnapshot() throws Exception {
        ClassifiedVehiclePosition snapshot = snapshot();
        when(valueOps.get("gps:snapshot:A12345"))
                .thenReturn(objectMapper.writeValueAsString(snapshot));

        Optional<ClassifiedVehiclePosition> found = store.find("A12345");

        assertThat(found).contains(snapshot);
    }

    @Test
    void shouldReturnEmptyWhenSnapshotMissing() {
        when(valueOps.get("gps:snapshot:A99999")).thenReturn(null);

        assertThat(store.find("A99999")).isEmpty();
    }
}

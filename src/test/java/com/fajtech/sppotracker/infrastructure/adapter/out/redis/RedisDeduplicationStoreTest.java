package com.fajtech.sppotracker.infrastructure.adapter.out.redis;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.infrastructure.config.DeduplicationProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Lógica do adapter de deduplicação sobre um StringRedisTemplate mockado (§3.2). */
class RedisDeduplicationStoreTest {

    private static final Duration TTL = Duration.ofMinutes(10);
    private static final Instant POS_TS = Instant.ofEpochMilli(1690000000000L);
    private static final Instant SENT_TS = Instant.ofEpochMilli(1690000002000L);

    private ValueOperations<String, String> valueOps;
    private RedisDeduplicationStore store;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        valueOps = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        store = new RedisDeduplicationStore(redisTemplate, new DeduplicationProperties(TTL));
    }

    private static VehiclePosition position() {
        return VehiclePosition.builder()
                .vehicleId("A12345")
                .coordinates(new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2")))
                .positionTimestamp(POS_TS)
                .sentTimestamp(SENT_TS)
                .receivedAt(Instant.now())
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
    }

    private String expectedKey() {
        return "gps:dedup:A12345:" + POS_TS.toEpochMilli() + ":" + SENT_TS.toEpochMilli();
    }

    @Test
    void shouldRegisterNewPositionWithTtlAndReportNotDuplicate() {
        when(valueOps.setIfAbsent(eq(expectedKey()), eq("1"), eq(TTL))).thenReturn(true);

        assertThat(store.isDuplicate(position())).isFalse();
        verify(valueOps).setIfAbsent(expectedKey(), "1", TTL);
    }

    @Test
    void shouldReportDuplicateWhenKeyAlreadyExists() {
        when(valueOps.setIfAbsent(eq(expectedKey()), eq("1"), eq(TTL))).thenReturn(false);

        assertThat(store.isDuplicate(position())).isTrue();
    }

    @Test
    void shouldTreatNullReplyAsNotDuplicate() {
        when(valueOps.setIfAbsent(eq(expectedKey()), eq("1"), eq(TTL))).thenReturn(null);

        assertThat(store.isDuplicate(position())).isFalse();
    }
}

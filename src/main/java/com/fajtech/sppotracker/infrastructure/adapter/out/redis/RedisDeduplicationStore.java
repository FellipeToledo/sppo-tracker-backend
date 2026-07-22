package com.fajtech.sppotracker.infrastructure.adapter.out.redis;

import com.fajtech.sppotracker.application.port.out.DeduplicationPort;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.infrastructure.config.DeduplicationProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Adapter de deduplicação sobre Redis (docs/regras-de-negocio.md §3.2). Usa
 * {@code SET key 1 NX EX ttl} (via {@code setIfAbsent}) para marcar a chave
 * {@code vehicleId:positionTs:sentTs} de forma atômica: se já existir, a posição
 * é duplicada.
 */
@Component
public class RedisDeduplicationStore implements DeduplicationPort {

    private static final String KEY_PREFIX = "gps:dedup:";
    private static final String MARKER = "1";

    private final StringRedisTemplate redisTemplate;
    private final DeduplicationProperties properties;

    public RedisDeduplicationStore(StringRedisTemplate redisTemplate, DeduplicationProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    @Override
    public boolean isDuplicate(VehiclePosition position) {
        String key = key(position);
        Boolean firstTime = redisTemplate.opsForValue()
                .setIfAbsent(key, MARKER, properties.deduplicationTtl());
        // firstTime == true  → marcou agora (nova); false → já existia (duplicada).
        // null (inesperado) → tratamos como nova, para não descartar posição válida.
        return Boolean.FALSE.equals(firstTime);
    }

    private static String key(VehiclePosition position) {
        return KEY_PREFIX
                + position.vehicleId() + ':'
                + position.positionTimestamp().toEpochMilli() + ':'
                + position.sentTimestamp().toEpochMilli();
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.out.redis;

import com.fajtech.sppotracker.application.port.out.CurrentSnapshotStorePort;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.infrastructure.config.CurrentSnapshotProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Snapshot atual por veículo em Redis (docs/regras-de-negocio.md §3.4). Guarda a
 * {@link ClassifiedVehiclePosition} serializada em JSON sob a chave
 * {@code gps:snapshot:{vehicleId}} com TTL; ao expirar, o veículo sai do "current".
 */
@Component
public class RedisCurrentSnapshotStore implements CurrentSnapshotStorePort {

    private static final String KEY_PREFIX = "gps:snapshot:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CurrentSnapshotProperties properties;

    public RedisCurrentSnapshotStore(StringRedisTemplate redisTemplate,
                                     ObjectMapper objectMapper,
                                     CurrentSnapshotProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public Optional<ClassifiedVehiclePosition> find(String vehicleId) {
        String json = redisTemplate.opsForValue().get(key(vehicleId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, ClassifiedVehiclePosition.class));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao desserializar snapshot do veículo " + vehicleId, e);
        }
    }

    @Override
    public void save(ClassifiedVehiclePosition snapshot) {
        String vehicleId = snapshot.position().vehicleId();
        String json;
        try {
            json = objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar snapshot do veículo " + vehicleId, e);
        }
        redisTemplate.opsForValue().set(key(vehicleId), json, properties.currentSnapshotTtl());
    }

    private static String key(String vehicleId) {
        return KEY_PREFIX + vehicleId;
    }
}

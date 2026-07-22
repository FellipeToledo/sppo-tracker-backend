package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração do snapshot atual (docs/regras-de-negocio.md §3.4, §8). Vinculada
 * a {@code gps.cache}. O TTL deve ser maior que o stale-threshold.
 *
 * @param currentSnapshotTtl TTL do snapshot atual por veículo
 */
@ConfigurationProperties(prefix = "gps.cache")
public record CurrentSnapshotProperties(Duration currentSnapshotTtl) {
}

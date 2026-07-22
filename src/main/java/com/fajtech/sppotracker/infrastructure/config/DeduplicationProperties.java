package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração da deduplicação (docs/regras-de-negocio.md §3.2, §8). Vinculada a
 * {@code gps.polling}, lendo apenas {@code deduplication-ttl}.
 *
 * @param deduplicationTtl TTL das chaves de deduplicação
 */
@ConfigurationProperties(prefix = "gps.polling")
public record DeduplicationProperties(Duration deduplicationTtl) {
}

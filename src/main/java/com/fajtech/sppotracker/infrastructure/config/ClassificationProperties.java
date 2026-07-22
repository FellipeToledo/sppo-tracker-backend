package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração da classificação (docs/regras-de-negocio.md §4.3, §8). Vinculada a
 * {@code gps.classification}. A chave {@code out-of-route-distance-threshold-meters}
 * pertence à regra de rota (fatia posterior) e é ignorada aqui.
 *
 * @param stalePositionThreshold idade a partir da qual a posição é STALE
 */
@ConfigurationProperties(prefix = "gps.classification")
public record ClassificationProperties(Duration stalePositionThreshold) {
}

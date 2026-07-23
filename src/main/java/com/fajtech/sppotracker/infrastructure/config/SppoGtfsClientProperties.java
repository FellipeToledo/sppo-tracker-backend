package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cliente HTTP do {@code sppo-gtfs-service} (docs/regras-de-negocio.md §4.4, §10).
 * Vinculado a {@code gps.gtfs-service}.
 *
 * @param baseUrl        URL base do serviço GTFS (ex.: {@code http://sppo-gtfs-service:8080})
 * @param apiKey         valor de {@code X-Api-Key}; vazio = sem cabeçalho
 * @param requestTimeout timeout por chamada
 */
@ConfigurationProperties(prefix = "gps.gtfs-service")
public record SppoGtfsClientProperties(String baseUrl, String apiKey, Duration requestTimeout) {

    public SppoGtfsClientProperties {
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            requestTimeout = Duration.ofSeconds(5);
        }
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}

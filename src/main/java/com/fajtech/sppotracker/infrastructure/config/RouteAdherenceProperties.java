package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração da regra de aderência de rota / OUT_OF_ROUTE (docs/regras-de-negocio.md
 * §4.4, §8). Vinculada a {@code gps.route}.
 *
 * @param shapeSource                    fonte de geometria: {@code disabled} (default) ou
 *                                       {@code gtfs-service}. Com {@code disabled} a regra
 *                                       nem é montada (comportamento atual do feed público).
 * @param corridorMeters                 meia-largura do corredor do traçado (default 15 m)
 * @param fallbackDistanceThresholdMeters limiar de distância para traçado degenerado (default 100 m)
 * @param cacheTtl                       validade da geometria em cache antes de re-resolver
 * @param refreshPoolSize                threads de background para resolução de shapes
 * @param refreshQueueCapacity           fila do executor de background (bound; excesso é descartado)
 */
@ConfigurationProperties(prefix = "gps.route")
public record RouteAdherenceProperties(
        ShapeSource shapeSource,
        double corridorMeters,
        double fallbackDistanceThresholdMeters,
        Duration cacheTtl,
        int refreshPoolSize,
        int refreshQueueCapacity) {

    public RouteAdherenceProperties {
        if (shapeSource == null) {
            shapeSource = ShapeSource.DISABLED;
        }
        if (corridorMeters <= 0) {
            corridorMeters = 15.0;
        }
        if (fallbackDistanceThresholdMeters <= 0) {
            fallbackDistanceThresholdMeters = 100.0;
        }
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            cacheTtl = Duration.ofHours(6);
        }
        if (refreshPoolSize <= 0) {
            refreshPoolSize = 2;
        }
        if (refreshQueueCapacity <= 0) {
            refreshQueueCapacity = 500;
        }
    }

    /** Fontes de itinerário suportadas (docs/regras-de-negocio.md §8, {@code ROUTE_SHAPE_SOURCE}). */
    public enum ShapeSource {
        DISABLED,
        GTFS_SERVICE
    }
}

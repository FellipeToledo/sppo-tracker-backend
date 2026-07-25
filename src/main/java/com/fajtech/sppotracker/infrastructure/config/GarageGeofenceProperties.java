package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Geofence de garagem (docs/regras-de-negocio.md §4.3, §8). Vinculada a
 * {@code gps.geofence.garages}.
 *
 * @param enabled  liga a regra {@code GARAGE_GEOFENCE}; com {@code false} nenhum bean
 *                 é criado e {@code IN_GARAGE} volta a depender só do código de serviço
 * @param resource localização do GeoJSON dos polígonos. Aceita {@code classpath:} (o
 *                 dataset empacotado) ou {@code file:} — assim dá para atualizar as
 *                 garagens num deploy montando o arquivo, sem rebuild
 */
@ConfigurationProperties(prefix = "gps.geofence.garages")
public record GarageGeofenceProperties(boolean enabled, String resource) {

    private static final String DEFAULT_RESOURCE = "classpath:geofences/garages.geojson";

    public GarageGeofenceProperties {
        if (resource == null || resource.isBlank()) {
            resource = DEFAULT_RESOURCE;
        }
    }
}

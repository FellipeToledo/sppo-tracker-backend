package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Bounding box do município do Rio (docs/regras-de-negocio.md §4.3, §8). Vinculada
 * a {@code gps.geofence.rio-municipality}.
 */
@ConfigurationProperties(prefix = "gps.geofence.rio-municipality")
public record RioMunicipalityProperties(
        BigDecimal minLatitude,
        BigDecimal maxLatitude,
        BigDecimal minLongitude,
        BigDecimal maxLongitude) {
}

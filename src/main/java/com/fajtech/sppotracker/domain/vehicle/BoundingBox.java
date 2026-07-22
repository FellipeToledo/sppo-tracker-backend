package com.fajtech.sppotracker.domain.vehicle;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Caixa geográfica (bounding box) usada pela regra de município
 * (docs/regras-de-negocio.md §4.3). Limites inclusivos. Value object puro; a
 * configuração externa mapeia para ele na borda.
 */
public record BoundingBox(BigDecimal minLatitude, BigDecimal maxLatitude,
                          BigDecimal minLongitude, BigDecimal maxLongitude) {

    public BoundingBox {
        Objects.requireNonNull(minLatitude, "minLatitude");
        Objects.requireNonNull(maxLatitude, "maxLatitude");
        Objects.requireNonNull(minLongitude, "minLongitude");
        Objects.requireNonNull(maxLongitude, "maxLongitude");
        if (minLatitude.compareTo(maxLatitude) > 0 || minLongitude.compareTo(maxLongitude) > 0) {
            throw new IllegalArgumentException("min bounds must not exceed max bounds");
        }
    }

    public boolean contains(Coordinates coordinates) {
        BigDecimal lat = coordinates.latitude();
        BigDecimal lon = coordinates.longitude();
        return lat.compareTo(minLatitude) >= 0 && lat.compareTo(maxLatitude) <= 0
                && lon.compareTo(minLongitude) >= 0 && lon.compareTo(maxLongitude) <= 0;
    }
}

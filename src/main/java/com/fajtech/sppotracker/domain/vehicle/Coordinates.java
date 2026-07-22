package com.fajtech.sppotracker.domain.vehicle;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Value object de coordenadas geográficas. Exemplo do PADRÃO de domínio:
 * imutável (record), auto-validado no construtor, sem dependência de framework.
 */
public record Coordinates(BigDecimal latitude, BigDecimal longitude) {

    private static final BigDecimal MIN_LATITUDE = BigDecimal.valueOf(-90);
    private static final BigDecimal MAX_LATITUDE = BigDecimal.valueOf(90);
    private static final BigDecimal MIN_LONGITUDE = BigDecimal.valueOf(-180);
    private static final BigDecimal MAX_LONGITUDE = BigDecimal.valueOf(180);

    public Coordinates {
        Objects.requireNonNull(latitude, "latitude must not be null");
        Objects.requireNonNull(longitude, "longitude must not be null");
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw new IllegalArgumentException("latitude must be between -90 and 90");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw new IllegalArgumentException("longitude must be between -180 and 180");
        }
    }

    /** (0,0) = coordenada inválida (regra INVALID). */
    public boolean isZeroZero() {
        return latitude.signum() == 0 && longitude.signum() == 0;
    }
}

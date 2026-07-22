package com.fajtech.sppotracker.domain.vehicle;

import java.time.Instant;
import java.util.Objects;

/**
 * Posição GPS de um veículo — value object imutável e auto-validado
 * (docs/regras-de-negocio.md §2.1). Segue o padrão de {@link Coordinates}:
 * validação no compact constructor, sem dependência de framework.
 *
 * <p>Invariantes:
 * <ul>
 *   <li>{@code vehicleId} obrigatório, não-vazio (armazenado com trim);</li>
 *   <li>{@code coordinates}, {@code positionTimestamp}, {@code sentTimestamp},
 *       {@code receivedAt} e {@code source} obrigatórios;</li>
 *   <li>{@code speed} negativa é normalizada para {@code null};</li>
 *   <li>{@code serviceCode}, {@code directionCode}, {@code routeId},
 *       {@code tripId}, {@code shapeId}, {@code heading} e
 *       {@code serverTimestamp} são opcionais.</li>
 * </ul>
 *
 * <p>Tempo sempre em UTC ({@link Instant}); a conversão de fuso ocorre só na borda.
 */
public record VehiclePosition(
        String vehicleId,
        String serviceCode,
        String directionCode,
        String routeId,
        String tripId,
        String shapeId,
        Coordinates coordinates,
        Double speed,
        Integer heading,
        Instant positionTimestamp,
        Instant sentTimestamp,
        Instant serverTimestamp,
        Instant receivedAt,
        PositionSource source) {

    public VehiclePosition {
        vehicleId = requireNonBlank(vehicleId, "vehicleId");
        Objects.requireNonNull(coordinates, "coordinates must not be null");
        Objects.requireNonNull(positionTimestamp, "positionTimestamp must not be null");
        Objects.requireNonNull(sentTimestamp, "sentTimestamp must not be null");
        Objects.requireNonNull(receivedAt, "receivedAt must not be null");
        Objects.requireNonNull(source, "source must not be null");
        if (speed != null && speed < 0) {
            speed = null;
        }
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Builder para a construção ergonômica dos 14 campos; a validação vive no record. */
    public static final class Builder {
        private String vehicleId;
        private String serviceCode;
        private String directionCode;
        private String routeId;
        private String tripId;
        private String shapeId;
        private Coordinates coordinates;
        private Double speed;
        private Integer heading;
        private Instant positionTimestamp;
        private Instant sentTimestamp;
        private Instant serverTimestamp;
        private Instant receivedAt;
        private PositionSource source;

        private Builder() {
        }

        public Builder vehicleId(String vehicleId) {
            this.vehicleId = vehicleId;
            return this;
        }

        public Builder serviceCode(String serviceCode) {
            this.serviceCode = serviceCode;
            return this;
        }

        public Builder directionCode(String directionCode) {
            this.directionCode = directionCode;
            return this;
        }

        public Builder routeId(String routeId) {
            this.routeId = routeId;
            return this;
        }

        public Builder tripId(String tripId) {
            this.tripId = tripId;
            return this;
        }

        public Builder shapeId(String shapeId) {
            this.shapeId = shapeId;
            return this;
        }

        public Builder coordinates(Coordinates coordinates) {
            this.coordinates = coordinates;
            return this;
        }

        public Builder speed(Double speed) {
            this.speed = speed;
            return this;
        }

        public Builder heading(Integer heading) {
            this.heading = heading;
            return this;
        }

        public Builder positionTimestamp(Instant positionTimestamp) {
            this.positionTimestamp = positionTimestamp;
            return this;
        }

        public Builder sentTimestamp(Instant sentTimestamp) {
            this.sentTimestamp = sentTimestamp;
            return this;
        }

        public Builder serverTimestamp(Instant serverTimestamp) {
            this.serverTimestamp = serverTimestamp;
            return this;
        }

        public Builder receivedAt(Instant receivedAt) {
            this.receivedAt = receivedAt;
            return this;
        }

        public Builder source(PositionSource source) {
            this.source = source;
            return this;
        }

        public VehiclePosition build() {
            return new VehiclePosition(
                    vehicleId, serviceCode, directionCode, routeId, tripId, shapeId,
                    coordinates, speed, heading,
                    positionTimestamp, sentTimestamp, serverTimestamp, receivedAt, source);
        }
    }
}

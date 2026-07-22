package com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppotracker.domain.vehicle.ClassificationTag;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Representação REST de uma posição classificada (docs/regras-de-negocio.md §7.1).
 * Achatado a partir de {@link ClassifiedVehiclePosition}.
 */
public record VehiclePositionResponse(
        String vehicleId,
        String serviceCode,
        String directionCode,
        String routeId,
        String tripId,
        String shapeId,
        BigDecimal latitude,
        BigDecimal longitude,
        Double speed,
        Integer heading,
        Instant positionTimestamp,
        Instant sentTimestamp,
        Instant serverTimestamp,
        Instant receivedAt,
        String source,
        VehiclePositionStatus classificationStatus,
        boolean valid,
        boolean insideMunicipality,
        boolean insideGarage,
        boolean onRoute,
        List<ClassificationTag> tags,
        String operatorName) {

    public static VehiclePositionResponse from(ClassifiedVehiclePosition classified, String operatorName) {
        VehiclePosition p = classified.position();
        PositionClassification c = classified.classification();
        return new VehiclePositionResponse(
                p.vehicleId(),
                p.serviceCode(),
                p.directionCode(),
                p.routeId(),
                p.tripId(),
                p.shapeId(),
                p.coordinates().latitude(),
                p.coordinates().longitude(),
                p.speed(),
                p.heading(),
                p.positionTimestamp(),
                p.sentTimestamp(),
                p.serverTimestamp(),
                p.receivedAt(),
                p.source().name(),
                c.status(),
                c.valid(),
                c.insideMunicipality(),
                c.insideGarage(),
                c.onRoute(),
                List.copyOf(c.tags()),
                operatorName);
    }
}

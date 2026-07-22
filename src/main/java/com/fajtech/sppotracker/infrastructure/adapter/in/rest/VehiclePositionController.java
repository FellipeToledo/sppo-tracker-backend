package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.GetCurrentVehiclePositionsUseCase;
import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.application.query.VehiclePositionFilter;
import com.fajtech.sppotracker.domain.operator.Operator;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.VehiclePositionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint do snapshot atual da frota (docs/regras-de-negocio.md §7.1). Adapter de
 * entrada fino: delega ao caso de uso e mapeia para DTO.
 */
@RestController
@RequestMapping("/api/v1/vehicle-positions")
public class VehiclePositionController {

    private final GetCurrentVehiclePositionsUseCase useCase;
    private final OperatorQueryUseCase operators;

    public VehiclePositionController(GetCurrentVehiclePositionsUseCase useCase, OperatorQueryUseCase operators) {
        this.useCase = useCase;
        this.operators = operators;
    }

    @GetMapping("/current")
    public List<VehiclePositionResponse> current(
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) String routeId,
            @RequestParam(required = false) VehiclePositionStatus classificationStatus) {
        VehiclePositionFilter filter = new VehiclePositionFilter(serviceCode, routeId, classificationStatus);
        return useCase.getCurrent(filter).stream()
                .map(this::toResponse)
                .toList();
    }

    private VehiclePositionResponse toResponse(ClassifiedVehiclePosition classified) {
        String operatorName = operators.resolve(classified.position().vehicleId())
                .map(Operator::name)
                .orElse(null);
        return VehiclePositionResponse.from(classified, operatorName);
    }
}

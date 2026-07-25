package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.GetRouteDeviationHistoryUseCase;
import com.fajtech.sppotracker.application.route.RouteDeviationQuery;
import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.DeviationSeverity;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.RouteDeviationHistoryResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Histórico de eventos de desvio de itinerário (docs/regras-de-negocio.md §5.5, §7.1).
 * Adapter de entrada fino: monta o filtro e delega ao caso de uso.
 */
@RestController
@RequestMapping("/api/v1/route-deviations")
public class RouteDeviationController {

    private final GetRouteDeviationHistoryUseCase useCase;

    public RouteDeviationController(GetRouteDeviationHistoryUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<RouteDeviationHistoryResponse> history(
            @RequestParam(required = false) String vehicleId,
            @RequestParam(required = false) String serviceCode,
            @RequestParam(required = false) DeviationEventType type,
            @RequestParam(required = false) DeviationSeverity severity,
            @RequestParam(required = false, defaultValue = "100") int limit) {
        RouteDeviationQuery query = new RouteDeviationQuery(vehicleId, serviceCode, type, severity, limit);
        return useCase.recent(query).stream()
                .map(RouteDeviationHistoryResponse::from)
                .toList();
    }
}

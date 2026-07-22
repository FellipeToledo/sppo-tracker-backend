package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.GetGpsPollingStatusUseCase;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.PollingStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint de status do último ciclo de polling (docs/regras-de-negocio.md §7.1).
 * 204 quando nenhum ciclo rodou ainda.
 */
@RestController
@RequestMapping("/api/v1/gps-polling")
public class GpsPollingStatusController {

    private final GetGpsPollingStatusUseCase useCase;

    public GpsPollingStatusController(GetGpsPollingStatusUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping("/status")
    public ResponseEntity<PollingStatusResponse> status() {
        return useCase.lastStatus()
                .map(PollingStatusResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }
}

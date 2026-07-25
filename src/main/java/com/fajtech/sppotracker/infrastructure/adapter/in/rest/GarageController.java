package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.GarageQueryUseCase;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.GarageCollectionResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Geofences de garagem carregadas (docs/regras-de-negocio.md §4.3, §7.1). Serve o
 * mapa de acompanhamento e a auditoria do dataset — é o jeito de ver, em produção,
 * exatamente quais polígonos estão valendo.
 *
 * <p>Adapter de entrada fino: delega ao caso de uso e mapeia para GeoJSON. Devolve
 * uma coleção vazia quando a geofence está desligada.
 */
@RestController
@RequestMapping("/api/v1/garages")
public class GarageController {

    private final GarageQueryUseCase useCase;

    public GarageController(GarageQueryUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public GarageCollectionResponse garages() {
        return GarageCollectionResponse.from(useCase.garages());
    }
}

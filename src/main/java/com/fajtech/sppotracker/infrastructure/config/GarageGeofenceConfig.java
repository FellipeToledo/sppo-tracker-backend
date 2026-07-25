package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.application.geofence.GarageService;
import com.fajtech.sppotracker.application.port.in.GarageQueryUseCase;
import com.fajtech.sppotracker.application.port.out.GarageGeofencePort;
import com.fajtech.sppotracker.domain.geofence.GarageLocator;
import com.fajtech.sppotracker.domain.vehicle.GarageGeofenceRule;
import com.fajtech.sppotracker.infrastructure.adapter.out.geofence.GeoJsonGarageGeofenceAdapter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Wiring da geofence de garagem (docs/regras-de-negocio.md §4.3, §7.1).
 *
 * <p>Os beans existem sempre; o que {@code gps.geofence.garages.enabled=false} faz é
 * <b>não carregar polígono nenhum</b>. Com a lista vazia, a
 * {@link GarageGeofenceRule} fica neutra (classificação idêntica à de antes da fatia)
 * e {@code GET /api/v1/garages} devolve coleção vazia — sem bean opcional para os
 * adapters tratarem.
 *
 * <p>A regra entra no {@code ClassificationConfig} via {@code ObjectProvider}, como a
 * {@code OutOfRouteRule}; a precedência entre status continua fixa no classificador
 * (§4.2). O dataset é lido uma vez na inicialização, fora do hot path.
 */
@Configuration
@EnableConfigurationProperties(GarageGeofenceProperties.class)
public class GarageGeofenceConfig {

    @Bean
    public GarageGeofencePort garageGeofencePort(ResourceLoader resourceLoader,
                                                GarageGeofenceProperties properties,
                                                ObjectMapper objectMapper) {
        if (!properties.enabled()) {
            return List::of;
        }
        return new GeoJsonGarageGeofenceAdapter(
                resourceLoader.getResource(properties.resource()), objectMapper);
    }

    @Bean
    public GarageLocator garageLocator(GarageGeofencePort port) {
        return new GarageLocator(port.garages());
    }

    @Bean
    public GarageQueryUseCase garageQueryUseCase(GarageLocator locator) {
        return new GarageService(locator);
    }

    @Bean
    public GarageGeofenceRule garageGeofenceRule(GarageLocator locator) {
        return new GarageGeofenceRule(locator);
    }
}

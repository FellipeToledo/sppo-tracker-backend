package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.application.port.out.GarageGeofencePort;
import com.fajtech.sppotracker.domain.vehicle.GarageGeofenceRule;
import com.fajtech.sppotracker.infrastructure.adapter.out.geofence.GeoJsonGarageGeofenceAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import tools.jackson.databind.ObjectMapper;

/**
 * Wiring da geofence de garagem (docs/regras-de-negocio.md §4.3). Condicional a
 * {@code gps.geofence.garages.enabled} (default {@code true}): desligado, nenhum bean
 * é criado e {@code IN_GARAGE} volta a depender apenas do código de serviço.
 *
 * <p>A {@link GarageGeofenceRule} resultante é coletada pelo {@link ClassificationConfig}
 * e adicionada ao classificador — a precedência entre status continua fixa lá (§4.2).
 * O dataset é lido uma vez na inicialização, fora do hot path.
 */
@Configuration
@EnableConfigurationProperties(GarageGeofenceProperties.class)
@ConditionalOnProperty(prefix = "gps.geofence.garages", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class GarageGeofenceConfig {

    @Bean
    public GarageGeofencePort garageGeofencePort(ResourceLoader resourceLoader,
                                                GarageGeofenceProperties properties,
                                                ObjectMapper objectMapper) {
        return new GeoJsonGarageGeofenceAdapter(
                resourceLoader.getResource(properties.resource()), objectMapper);
    }

    @Bean
    public GarageGeofenceRule garageGeofenceRule(GarageGeofencePort port) {
        return new GarageGeofenceRule(port.garages());
    }
}

package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.domain.vehicle.BoundingBox;
import com.fajtech.sppotracker.domain.vehicle.ClassificationRule;
import com.fajtech.sppotracker.domain.vehicle.GarageServiceCodeRule;
import com.fajtech.sppotracker.domain.vehicle.InvalidCoordinatesRule;
import com.fajtech.sppotracker.domain.vehicle.OutOfMunicipalityRule;
import com.fajtech.sppotracker.domain.vehicle.OutOfRouteRule;
import com.fajtech.sppotracker.domain.vehicle.PositionClassifier;
import com.fajtech.sppotracker.domain.vehicle.StalePositionRule;
import com.fajtech.sppotracker.domain.vehicle.SuspiciousServiceCodeRule;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Wiring da classificação (docs/regras-de-negocio.md §4). Monta o bounding box a
 * partir da configuração e o {@link PositionClassifier} com a lista de regras
 * ativas. A regra {@link OutOfRouteRule} é opcional: só existe quando a fonte de
 * shapes está habilitada ({@code gps.route.shape-source=gtfs-service}, ver
 * {@code RouteAdherenceConfig}); a precedência entre status permanece fixa no
 * classificador, independente da ordem das regras.
 */
@Configuration
@EnableConfigurationProperties({RioMunicipalityProperties.class, ClassificationProperties.class})
public class ClassificationConfig {

    @Bean
    public BoundingBox rioMunicipalityBox(RioMunicipalityProperties properties) {
        return new BoundingBox(
                properties.minLatitude(), properties.maxLatitude(),
                properties.minLongitude(), properties.maxLongitude());
    }

    @Bean
    public PositionClassifier positionClassifier(BoundingBox rioMunicipalityBox,
                                                 ClassificationProperties properties,
                                                 ObjectProvider<OutOfRouteRule> outOfRouteRule) {
        List<ClassificationRule> rules = new ArrayList<>(List.of(
                new InvalidCoordinatesRule(),
                new OutOfMunicipalityRule(rioMunicipalityBox),
                new GarageServiceCodeRule(),
                new SuspiciousServiceCodeRule(),
                new StalePositionRule(properties.stalePositionThreshold())));
        outOfRouteRule.ifAvailable(rules::add);
        return new PositionClassifier(rules);
    }
}

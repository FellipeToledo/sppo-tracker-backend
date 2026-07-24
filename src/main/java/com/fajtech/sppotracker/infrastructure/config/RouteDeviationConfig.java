package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.application.port.out.PublishRouteDeviationEventPort;
import com.fajtech.sppotracker.application.port.out.RecordRouteDeviationEventPort;
import com.fajtech.sppotracker.application.route.RouteDeviationService;
import com.fajtech.sppotracker.domain.route.DeviationConfig;
import com.fajtech.sppotracker.domain.route.RouteAdherenceEvaluator;
import com.fajtech.sppotracker.domain.route.RouteDeviationDetector;
import com.fajtech.sppotracker.infrastructure.adapter.in.messaging.RouteDeviationPositionSubscriber;
import com.fajtech.sppotracker.infrastructure.adapter.in.scheduler.RouteDeviationSweepScheduler;
import com.fajtech.sppotracker.infrastructure.adapter.in.websocket.RouteDeviationEventRelay;
import com.fajtech.sppotracker.infrastructure.adapter.out.messaging.RedisRouteDeviationEventPublisher;
import com.fajtech.sppotracker.infrastructure.adapter.out.persistence.JpaRouteDeviationEventWriter;
import com.fajtech.sppotracker.infrastructure.adapter.out.persistence.RouteDeviationEventRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;

/**
 * Wiring da máquina de desvio de itinerário (docs/regras-de-negocio.md §5). Gated pela
 * fonte de shapes ({@code gps.route.shape-source=gtfs-service}) — a detecção depende de
 * geometria — e, para o serviço/subscriber/relay/scheduler, também por
 * {@code gps.route.deviation.enabled} (default true). A detecção roda <b>fora</b> do hot
 * path: consome o stream de posições publicado e evolui a máquina por veículo.
 */
@Configuration
@EnableConfigurationProperties(RouteDeviationProperties.class)
@ConditionalOnProperty(prefix = "gps.route", name = "shape-source", havingValue = "gtfs-service")
public class RouteDeviationConfig {

    @Bean
    public DeviationConfig deviationConfig(RouteDeviationProperties properties) {
        return new DeviationConfig(
                properties.confirmationMarginMeters(), properties.alertPoints(),
                properties.confirmSustained(), properties.confirmDistanceMeters(),
                properties.returnPoints(), properties.severityMedioMeters(), properties.severityGraveMeters());
    }

    @Bean
    public RouteDeviationDetector routeDeviationDetector() {
        return new RouteDeviationDetector();
    }

    @Bean
    public RecordRouteDeviationEventPort recordRouteDeviationEventPort(RouteDeviationEventRepository repository) {
        return new JpaRouteDeviationEventWriter(repository);
    }

    @Bean
    public PublishRouteDeviationEventPort publishRouteDeviationEventPort(StringRedisTemplate redisTemplate,
                                                                        ObjectMapper objectMapper) {
        return new RedisRouteDeviationEventPublisher(redisTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gps.route.deviation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RouteDeviationService routeDeviationService(RouteAdherenceEvaluator routeAdherenceEvaluator,
                                                       RouteDeviationDetector routeDeviationDetector,
                                                       DeviationConfig deviationConfig,
                                                       Clock clock,
                                                       RouteDeviationProperties properties,
                                                       RecordRouteDeviationEventPort recorder,
                                                       PublishRouteDeviationEventPort publisher) {
        return new RouteDeviationService(routeAdherenceEvaluator, routeDeviationDetector, deviationConfig,
                clock, properties.stateTtl(), recorder, publisher);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gps.route.deviation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RouteDeviationPositionSubscriber routeDeviationPositionSubscriber(RouteDeviationService routeDeviationService,
                                                                            ObjectMapper objectMapper) {
        return new RouteDeviationPositionSubscriber(routeDeviationService, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gps.route.deviation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RouteDeviationEventRelay routeDeviationEventRelay(SimpMessagingTemplate messagingTemplate,
                                                            ObjectMapper objectMapper) {
        return new RouteDeviationEventRelay(messagingTemplate, objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "gps.route.deviation", name = "enabled", havingValue = "true", matchIfMissing = true)
    public RouteDeviationSweepScheduler routeDeviationSweepScheduler(RouteDeviationService routeDeviationService) {
        return new RouteDeviationSweepScheduler(routeDeviationService);
    }
}

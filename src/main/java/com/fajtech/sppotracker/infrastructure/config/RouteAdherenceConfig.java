package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.application.port.out.ResolveRouteShapesPort;
import com.fajtech.sppotracker.application.route.RouteGeometryCache;
import com.fajtech.sppotracker.domain.route.RouteAdherenceEvaluator;
import com.fajtech.sppotracker.domain.vehicle.OutOfRouteRule;
import com.fajtech.sppotracker.infrastructure.adapter.out.gtfs.SppoGtfsShapeProvider;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Wiring da aderência de rota / OUT_OF_ROUTE (docs/regras-de-negocio.md §4.4). Todos
 * os beans são condicionais a {@code gps.route.shape-source=gtfs-service}: com a fonte
 * {@code disabled} (default) nada é criado — sem cliente HTTP, sem regra — preservando
 * o comportamento do feed público. A {@link OutOfRouteRule} resultante é coletada pelo
 * {@code ClassificationConfig} e adicionada ao classificador (a precedência é fixa lá).
 */
@Configuration
@EnableConfigurationProperties({RouteAdherenceProperties.class, SppoGtfsClientProperties.class})
@ConditionalOnProperty(prefix = "gps.route", name = "shape-source", havingValue = "gtfs-service")
public class RouteAdherenceConfig {

    @Bean
    public ResolveRouteShapesPort resolveRouteShapesPort(WebClient.Builder webClientBuilder,
                                                         SppoGtfsClientProperties properties,
                                                         MeterRegistry registry) {
        return new SppoGtfsShapeProvider(webClientBuilder, properties, registry);
    }

    /** Executor bounded para resolução em background; satura com AbortPolicy (§4.4). */
    @Bean(destroyMethod = "shutdown")
    public Executor routeShapeRefreshExecutor(RouteAdherenceProperties properties) {
        return new ThreadPoolExecutor(
                1, properties.refreshPoolSize(),
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(properties.refreshQueueCapacity()),
                runnable -> {
                    Thread thread = new Thread(runnable, "route-shape-refresh");
                    thread.setDaemon(true);
                    return thread;
                },
                new ThreadPoolExecutor.AbortPolicy());
    }

    @Bean
    public RouteGeometryCache routeGeometryCache(ResolveRouteShapesPort port,
                                                 Executor routeShapeRefreshExecutor,
                                                 Clock clock,
                                                 RouteAdherenceProperties properties) {
        return new RouteGeometryCache(port, routeShapeRefreshExecutor, clock, properties.cacheTtl());
    }

    @Bean
    public RouteAdherenceEvaluator routeAdherenceEvaluator(RouteGeometryCache routeGeometryCache,
                                                           RouteAdherenceProperties properties) {
        return new RouteAdherenceEvaluator(routeGeometryCache,
                properties.corridorMeters(), properties.fallbackDistanceThresholdMeters());
    }

    @Bean
    public OutOfRouteRule outOfRouteRule(RouteAdherenceEvaluator routeAdherenceEvaluator) {
        return new OutOfRouteRule(routeAdherenceEvaluator);
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.out.gtfs;

import com.fajtech.sppotracker.application.port.out.ResolveRouteShapesPort;
import com.fajtech.sppotracker.application.route.ResolvedShapes;
import com.fajtech.sppotracker.domain.route.RoutePath;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.infrastructure.config.SppoGtfsClientProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Adapter de saída que resolve a geometria de itinerário chamando o
 * {@code sppo-gtfs-service} ({@code GET /api/v1/lines/{lineCode}/shapes}) e implementa
 * {@link ResolveRouteShapesPort} (docs/regras-de-negocio.md §4.4).
 *
 * <p>Semântica:
 * <ul>
 *   <li>{@code 200} ⇒ decodifica os shapes (encoded polyline) em {@link RoutePath}; a
 *       resolução {@code no_shapes} vem como lista vazia — resultado autoritativo;</li>
 *   <li>{@code 404} (linha inexistente no feed) ⇒ resultado autoritativo vazio (cacheável);</li>
 *   <li>qualquer outra falha (timeout, 5xx, corpo inválido) ⇒ {@link Optional#empty()}
 *       (transitório: não cacheia, re-tenta depois).</li>
 * </ul>
 */
public class SppoGtfsShapeProvider implements ResolveRouteShapesPort {

    private static final Logger log = LoggerFactory.getLogger(SppoGtfsShapeProvider.class);
    private static final int MIN_POINTS = 2;

    private final WebClient webClient;
    private final SppoGtfsClientProperties properties;
    private final Counter resolveSuccess;
    private final Counter resolveEmpty;
    private final Counter resolveFailure;

    public SppoGtfsShapeProvider(WebClient.Builder webClientBuilder,
                                 SppoGtfsClientProperties properties,
                                 MeterRegistry registry) {
        this.properties = properties;
        WebClient.Builder builder = webClientBuilder.baseUrl(properties.baseUrl());
        if (properties.hasApiKey()) {
            builder = builder.defaultHeader("X-Api-Key", properties.apiKey());
        }
        this.webClient = builder.build();
        this.resolveSuccess = Counter.builder("gps.route.shape.resolve").tag("result", "shapes").register(registry);
        this.resolveEmpty = Counter.builder("gps.route.shape.resolve").tag("result", "empty").register(registry);
        this.resolveFailure = Counter.builder("gps.route.shape.resolve").tag("result", "failure").register(registry);
    }

    @Override
    public Optional<ResolvedShapes> resolve(String serviceCode) {
        try {
            LineShapesResponse response = webClient.get()
                    .uri("/api/v1/lines/{lineCode}/shapes", serviceCode)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.ACCEPT_ENCODING, "identity")
                    .retrieve()
                    .bodyToMono(LineShapesResponse.class)
                    .timeout(properties.requestTimeout())
                    .block();
            ResolvedShapes resolved = toResolvedShapes(response);
            if (resolved.paths().isEmpty()) {
                resolveEmpty.increment();
            } else {
                resolveSuccess.increment();
            }
            return Optional.of(resolved);
        } catch (WebClientResponseException.NotFound notFound) {
            // Linha não existe no feed: resultado autoritativo vazio (cacheável).
            resolveEmpty.increment();
            return Optional.of(ResolvedShapes.empty(null));
        } catch (RuntimeException failure) {
            resolveFailure.increment();
            log.debug("Falha ao resolver shapes da linha {}: {}", serviceCode, failure.toString());
            return Optional.empty();
        }
    }

    private static ResolvedShapes toResolvedShapes(LineShapesResponse response) {
        if (response == null || response.shapes() == null || response.shapes().isEmpty()) {
            return ResolvedShapes.empty(feedVersionId(response));
        }
        List<RoutePath> paths = new ArrayList<>();
        for (LineShapesResponse.Shape shape : response.shapes()) {
            if (shape == null || shape.encodedPolyline() == null) {
                continue;
            }
            List<Coordinates> points = EncodedPolyline.decode(shape.encodedPolyline());
            if (points.size() >= MIN_POINTS) {
                paths.add(RoutePath.of(shape.shapeId(), points));
            }
        }
        return new ResolvedShapes(feedVersionId(response), paths);
    }

    private static String feedVersionId(LineShapesResponse response) {
        return response == null || response.feedVersion() == null ? null : response.feedVersion().id();
    }
}

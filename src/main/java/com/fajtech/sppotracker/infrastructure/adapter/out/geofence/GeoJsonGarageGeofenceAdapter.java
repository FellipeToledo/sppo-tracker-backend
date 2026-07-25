package com.fajtech.sppotracker.infrastructure.adapter.out.geofence;

import com.fajtech.sppotracker.application.port.out.GarageGeofencePort;
import com.fajtech.sppotracker.domain.geofence.GarageArea;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Carrega os polígonos de garagem de um GeoJSON empacotado (docs/regras-de-negocio.md
 * §4.3), uma vez na inicialização — fora do hot path.
 *
 * <p>Espera um {@code FeatureCollection} com geometrias {@code Polygon} ou
 * {@code MultiPolygon} em WGS84, coordenadas na ordem {@code [lon, lat]} (RFC 7946).
 * Só o <b>anel externo</b> é usado: garagem não tem buraco, e um anel interno
 * ignorado erra para o lado de classificar dentro (conservador, §4.3).
 *
 * <p><b>Robustez:</b> falha ao ler/parsear o arquivo derruba a inicialização (é dado
 * empacotado: erro de build, melhor descobrir na hora); uma <i>feature</i> inválida
 * isolada é descartada com aviso, para que um polígono ruim não tire o serviço do ar.
 */
public class GeoJsonGarageGeofenceAdapter implements GarageGeofencePort {

    private static final Logger log = LoggerFactory.getLogger(GeoJsonGarageGeofenceAdapter.class);

    private final Resource resource;
    private final ObjectMapper objectMapper;
    private volatile List<GarageArea> garages = List.of();

    public GeoJsonGarageGeofenceAdapter(Resource resource, ObjectMapper objectMapper) {
        this.resource = resource;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        JsonNode root = read();
        List<GarageArea> parsed = new ArrayList<>();
        int skipped = 0;
        for (JsonNode feature : root.path("features")) {
            try {
                parsed.addAll(toAreas(feature));
            } catch (RuntimeException e) {
                skipped++;
                log.warn("Descartando geofence de garagem inválida em {}: {}",
                        resource.getFilename(), e.getMessage());
            }
        }
        garages = List.copyOf(parsed);
        // Mesma linha de log usada na validação em runtime (docs/estado-do-projeto.md §10).
        log.info("Loaded garage geofences. count={}{}", garages.size(),
                skipped == 0 ? "" : " (descartadas=" + skipped + ")");
    }

    @Override
    public List<GarageArea> garages() {
        return garages;
    }

    private JsonNode read() {
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException(
                    "Falha ao carregar as geofences de garagem de " + resource.getFilename(), e);
        }
    }

    private List<GarageArea> toAreas(JsonNode feature) {
        JsonNode geometry = feature.path("geometry");
        String type = geometry.path("type").asString();
        JsonNode properties = feature.path("properties");
        String id = text(properties, "garage_id");
        String name = text(properties, "garage_name");
        if (name.isBlank()) {
            name = text(properties, "operador");
        }
        String address = text(properties, "endereco");
        String neighborhood = text(properties, "bairro");
        JsonNode coordinates = geometry.path("coordinates");

        if ("Polygon".equals(type)) {
            return List.of(GarageArea.of(id, name, address, neighborhood, ring(coordinates.path(0))));
        }
        if ("MultiPolygon".equals(type)) {
            List<GarageArea> areas = new ArrayList<>();
            for (int i = 0; i < coordinates.size(); i++) {
                areas.add(GarageArea.of(id + "-" + (i + 1), name, address, neighborhood,
                        ring(coordinates.path(i).path(0))));
            }
            return areas;
        }
        throw new IllegalArgumentException("geometria não suportada: " + type);
    }

    private static List<Coordinates> ring(JsonNode ring) {
        if (!ring.isArray() || ring.isEmpty()) {
            throw new IllegalArgumentException("anel externo ausente");
        }
        List<Coordinates> points = new ArrayList<>(ring.size());
        for (JsonNode position : ring) {
            // RFC 7946: [longitude, latitude].
            points.add(new Coordinates(
                    BigDecimal.valueOf(position.path(1).asDouble()),
                    BigDecimal.valueOf(position.path(0).asDouble())));
        }
        return points;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asString().trim();
    }
}

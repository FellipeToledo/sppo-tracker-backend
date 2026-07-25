package com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppotracker.domain.geofence.GarageArea;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.math.BigDecimal;
import java.util.List;

/**
 * Garagens como <b>GeoJSON</b> {@code FeatureCollection} (RFC 7946), resposta de
 * {@code GET /api/v1/garages} (docs/regras-de-negocio.md §7.1).
 *
 * <p>GeoJSON — e não um formato próprio — porque o consumidor natural é um mapa
 * ({@code L.geoJSON} no Leaflet, QGIS, etc.) e o padrão fixa a ordem
 * {@code [longitude, latitude]}, que é a origem clássica de bug ao desenhar
 * polígonos. É também o formato do dataset de origem, então o dado sai como entrou.
 */
public record GarageCollectionResponse(String type, List<Feature> features) {

    public static GarageCollectionResponse from(List<GarageArea> garages) {
        return new GarageCollectionResponse("FeatureCollection",
                garages.stream().map(Feature::from).toList());
    }

    public record Feature(String type, Geometry geometry, Properties properties) {

        static Feature from(GarageArea garage) {
            return new Feature("Feature", Geometry.from(garage), Properties.from(garage));
        }
    }

    /** {@code Polygon}: um único anel externo, cada ponto {@code [lon, lat]}. */
    public record Geometry(String type, List<List<List<BigDecimal>>> coordinates) {

        static Geometry from(GarageArea garage) {
            List<List<BigDecimal>> ring = garage.ring().stream()
                    .map(Geometry::position)
                    .toList();
            return new Geometry("Polygon", List.of(ring));
        }

        private static List<BigDecimal> position(Coordinates c) {
            return List.of(c.longitude(), c.latitude());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Properties(String id, String name, String address, String neighborhood,
                             int pointCount) {

        static Properties from(GarageArea garage) {
            return new Properties(garage.id(), garage.name(), garage.address(),
                    garage.neighborhood(), garage.ring().size());
        }
    }
}

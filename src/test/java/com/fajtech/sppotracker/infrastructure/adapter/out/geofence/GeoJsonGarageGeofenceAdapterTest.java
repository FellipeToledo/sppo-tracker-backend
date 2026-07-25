package com.fajtech.sppotracker.infrastructure.adapter.out.geofence;

import com.fajtech.sppotracker.domain.geofence.GarageArea;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Carregamento das geofences de garagem do classpath (docs/regras-de-negocio.md §4.3).
 * Exercita o arquivo REAL empacotado — é o dado que vai para produção.
 */
class GeoJsonGarageGeofenceAdapterTest {

    /** Ponto verificado dentro do pátio da Braso Lisboa (Inhaúma), no dataset real. */
    private static final Coordinates DENTRO_BRASO_LISBOA = at(-22.868456, -43.293813);
    /** Praia de Copacabana: nenhuma garagem por perto. */
    private static final Coordinates COPACABANA = at(-22.9711, -43.1822);

    private GeoJsonGarageGeofenceAdapter adapter;

    private static Coordinates at(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    private static GeoJsonGarageGeofenceAdapter adapterFor(String json) {
        var adapter = new GeoJsonGarageGeofenceAdapter(
                new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)),
                JsonMapper.builder().build());
        adapter.load();
        return adapter;
    }

    @BeforeEach
    void setUp() {
        adapter = new GeoJsonGarageGeofenceAdapter(
                new ClassPathResource("geofences/garages.geojson"),
                JsonMapper.builder().build());
        adapter.load();
    }

    @Test
    void shouldLoadEveryPackagedGarage() {
        assertThat(adapter.garages()).hasSize(37);
        assertThat(adapter.garages())
                .allSatisfy(g -> {
                    assertThat(g.id()).isNotBlank();
                    assertThat(g.name()).isNotBlank();
                    assertThat(g.ring()).hasSizeGreaterThanOrEqualTo(4);
                });
    }

    @Test
    void shouldMatchAKnownPointInsideARealGarage() {
        assertThat(adapter.garages())
                .filteredOn(g -> g.contains(DENTRO_BRASO_LISBOA))
                .singleElement()
                .extracting(GarageArea::name)
                .asString()
                .contains("Braso Lisboa");
    }

    @Test
    void shouldNotMatchAPointFarFromEveryGarage() {
        assertThat(adapter.garages()).noneMatch(g -> g.contains(COPACABANA));
    }

    @Test
    void shouldReadCoordinatesInLonLatOrder() {
        // RFC 7946 é [lon, lat]; trocar a ordem jogaria as garagens para a China.
        assertThat(adapter.garages()).allSatisfy(g -> {
            assertThat(g.bounds().minLatitude()).isBetween(BigDecimal.valueOf(-23.2), BigDecimal.valueOf(-22.6));
            assertThat(g.bounds().minLongitude()).isBetween(BigDecimal.valueOf(-43.9), BigDecimal.valueOf(-43.0));
        });
    }

    @Test
    void shouldSplitMultiPolygonIntoOneAreaPerPolygon() {
        var multi = adapterFor("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"garage_id":7,"garage_name":"Duas quadras"},
                   "geometry":{"type":"MultiPolygon","coordinates":[
                     [[[-43.20,-22.90],[-43.19,-22.90],[-43.19,-22.89],[-43.20,-22.89],[-43.20,-22.90]]],
                     [[[-43.30,-22.85],[-43.29,-22.85],[-43.29,-22.84],[-43.30,-22.84],[-43.30,-22.85]]]
                   ]}}]}""");

        assertThat(multi.garages()).extracting(GarageArea::id).containsExactly("7-1", "7-2");
        assertThat(multi.garages()).allMatch(g -> g.name().equals("Duas quadras"));
    }

    @Test
    void shouldSkipInvalidFeatureAndKeepTheGoodOnes() {
        var mixed = adapterFor("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"garage_id":1,"garage_name":"Boa"},
                   "geometry":{"type":"Polygon","coordinates":[
                     [[-43.20,-22.90],[-43.19,-22.90],[-43.19,-22.89],[-43.20,-22.89],[-43.20,-22.90]]]}},
                  {"type":"Feature","properties":{"garage_id":2,"garage_name":"Degenerada"},
                   "geometry":{"type":"Polygon","coordinates":[[[-43.20,-22.90],[-43.19,-22.90]]]}},
                  {"type":"Feature","properties":{"garage_id":3,"garage_name":"Ponto"},
                   "geometry":{"type":"Point","coordinates":[-43.20,-22.90]}}]}""");

        assertThat(mixed.garages()).extracting(GarageArea::name).containsExactly("Boa");
    }

    @Test
    void shouldFallBackToOperadorWhenGarageNameIsAbsent() {
        // O arquivo oficial do data.rio nomeia a coluna "operador".
        var oficial = adapterFor("""
                {"type":"FeatureCollection","features":[
                  {"type":"Feature","properties":{"garage_id":9,"operador":"Viação Novacap"},
                   "geometry":{"type":"Polygon","coordinates":[
                     [[-43.20,-22.90],[-43.19,-22.90],[-43.19,-22.89],[-43.20,-22.89],[-43.20,-22.90]]]}}]}""");

        assertThat(oficial.garages()).singleElement()
                .extracting(GarageArea::name).isEqualTo("Viação Novacap");
    }

    @Test
    void shouldFailFastWhenTheDocumentIsNotAFeatureCollection() {
        // JSON válido mas estrutura errada produziria ZERO garagens em silêncio,
        // desligando o IN_GARAGE e o endpoint sem ninguém notar. É dado empacotado.
        assertThatThrownBy(() -> adapterFor("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FeatureCollection");

        assertThatThrownBy(() -> adapterFor("{\"type\":\"FeatureCollection\",\"features\":{}}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FeatureCollection");
    }

    @Test
    void shouldAcceptAnEmptyButWellFormedCollection() {
        // Coleção vazia é legítima (geofence sem polígonos): a regra fica neutra.
        assertThat(adapterFor("{\"type\":\"FeatureCollection\",\"features\":[]}").garages()).isEmpty();
    }

    @Test
    void shouldFailFastWhenTheResourceCannotBeRead() {
        var broken = new GeoJsonGarageGeofenceAdapter(
                new ClassPathResource("geofences/nao-existe.geojson"),
                JsonMapper.builder().build());

        assertThatThrownBy(broken::load)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("geofences de garagem");
    }
}

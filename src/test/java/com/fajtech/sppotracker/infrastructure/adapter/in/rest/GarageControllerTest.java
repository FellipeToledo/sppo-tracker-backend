package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.GarageQueryUseCase;
import com.fajtech.sppotracker.domain.geofence.GarageArea;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Geofences de garagem como GeoJSON: {@code GET /api/v1/garages} (§7.1). */
@WebMvcTest(GarageController.class)
class GarageControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GarageQueryUseCase useCase;

    private static Coordinates at(double lat, double lon) {
        return new Coordinates(BigDecimal.valueOf(lat), BigDecimal.valueOf(lon));
    }

    private static GarageArea garage() {
        return GarageArea.of("25", "Empresa de Transportes Braso Lisboa",
                "Rua Ferreira Nobre, 100", "Inhaúma", List.of(
                        at(-22.90, -43.20),
                        at(-22.90, -43.19),
                        at(-22.89, -43.19),
                        at(-22.89, -43.20)));
    }

    @Test
    void shouldReturnFeatureCollectionWithLonLatOrder() throws Exception {
        when(useCase.garages()).thenReturn(List.of(garage()));

        mockMvc.perform(get("/api/v1/garages"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features[0].type").value("Feature"))
                .andExpect(jsonPath("$.features[0].geometry.type").value("Polygon"))
                // RFC 7946: [lon, lat] — trocar a ordem joga a garagem para o outro lado do mundo.
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][0][0]").value(-43.20))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][0][1]").value(-22.90))
                .andExpect(jsonPath("$.features[0].properties.id").value("25"))
                .andExpect(jsonPath("$.features[0].properties.name")
                        .value("Empresa de Transportes Braso Lisboa"))
                .andExpect(jsonPath("$.features[0].properties.address").value("Rua Ferreira Nobre, 100"))
                .andExpect(jsonPath("$.features[0].properties.neighborhood").value("Inhaúma"));
    }

    @Test
    void shouldCloseTheRingInTheResponse() throws Exception {
        when(useCase.garages()).thenReturn(List.of(garage()));

        // O anel entrou aberto (4 pontos) e sai fechado (5), como manda o GeoJSON.
        mockMvc.perform(get("/api/v1/garages"))
                .andExpect(jsonPath("$.features[0].properties.pointCount").value(5))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0].length()").value(5))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][4][0]").value(-43.20))
                .andExpect(jsonPath("$.features[0].geometry.coordinates[0][4][1]").value(-22.90));
    }

    @Test
    void shouldReturnEmptyCollectionWhenGeofenceIsOff() throws Exception {
        when(useCase.garages()).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/garages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("FeatureCollection"))
                .andExpect(jsonPath("$.features").isEmpty());
    }

    @Test
    void shouldOmitDescriptiveFieldsWhenAbsent() throws Exception {
        when(useCase.garages()).thenReturn(List.of(GarageArea.of("g1", "Sem endereço", List.of(
                at(-22.90, -43.20), at(-22.90, -43.19), at(-22.89, -43.19)))));

        mockMvc.perform(get("/api/v1/garages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.features[0].properties.name").value("Sem endereço"))
                .andExpect(jsonPath("$.features[0].properties.address").doesNotExist())
                .andExpect(jsonPath("$.features[0].properties.neighborhood").doesNotExist());
    }
}

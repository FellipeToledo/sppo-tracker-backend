package com.fajtech.sppotracker.infrastructure.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(MapViewController.class)
class MapViewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldRenderMapViewWithReconcileInterval() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(view().name("map"))
                .andExpect(model().attributeExists("reconcileSeconds"));
    }

    @Test
    void shouldWireTheGarageLayerIntoTheView() throws Exception {
        // Guarda de regressão do template: o mapa precisa do toggle, do pane próprio
        // (para o polígono não cobrir o marcador) e da carga do GeoJSON (§4.3, §7.1).
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"garages\"")))
                .andExpect(content().string(containsString("createPane('garages')")))
                .andExpect(content().string(containsString("/api/v1/garages")));
    }

    @Test
    void shouldCategoriseUnevaluatedItineraryInsteadOfHidingIt() {
        // Nada é descartado: posição sem itinerário avaliado entra no mapa com borda
        // tracejada, linha no popup, entrada na legenda e contagem no rodapé (§4.4).
        assertThatCode(() -> mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ROUTE_NOT_PLANNED")))
                .andExpect(content().string(containsString("sem planejamento no feed")))
                .andExpect(content().string(containsString("dashArray")))
                // O transitório é interno: não pode vazar para a view.
                .andExpect(content().string(not(containsString("ROUTE_PENDING")))))
                .doesNotThrowAnyException();
    }
}

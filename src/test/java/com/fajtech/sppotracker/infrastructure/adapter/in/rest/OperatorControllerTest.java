package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.domain.operator.Company;
import com.fajtech.sppotracker.domain.operator.Consortium;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OperatorController.class)
class OperatorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OperatorQueryUseCase useCase;

    @Test
    void shouldReturnCompaniesEnrichedWithConsortium() throws Exception {
        when(useCase.getConsortiums()).thenReturn(List.of(
                new Consortium("A", "Consórcio Intersul"),
                new Consortium("C", "Consórcio Transcarioca")));
        when(useCase.getCompanies()).thenReturn(List.of(
                new Company("A410", "Real Auto Onibus Ltda"),
                new Company("C130", "Transportes Barra Ltda")));

        mockMvc.perform(get("/api/v1/operators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prefix").value("A410"))
                .andExpect(jsonPath("$[0].name").value("Real Auto Onibus Ltda"))
                .andExpect(jsonPath("$[0].consortiumCode").value("A"))
                .andExpect(jsonPath("$[0].consortiumName").value("Consórcio Intersul"))
                .andExpect(jsonPath("$[1].prefix").value("C130"))
                .andExpect(jsonPath("$[1].consortiumName").value("Consórcio Transcarioca"));
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.domain.operator.Operator;
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
    void shouldReturnOperators() throws Exception {
        when(useCase.getAll()).thenReturn(List.of(
                new Operator("A26I", "Empresa X"), new Operator("B28R", "Empresa Y")));

        mockMvc.perform(get("/api/v1/operators"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].prefix").value("A26I"))
                .andExpect(jsonPath("$[0].name").value("Empresa X"))
                .andExpect(jsonPath("$[1].prefix").value("B28R"));
    }
}

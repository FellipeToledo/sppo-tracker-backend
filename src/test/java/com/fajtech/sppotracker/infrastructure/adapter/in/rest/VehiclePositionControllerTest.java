package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.GetCurrentVehiclePositionsUseCase;
import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.application.query.VehiclePositionFilter;
import com.fajtech.sppotracker.domain.operator.Operator;
import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionClassification;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.domain.vehicle.VehiclePositionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VehiclePositionController.class)
class VehiclePositionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetCurrentVehiclePositionsUseCase useCase;

    @MockitoBean
    private OperatorQueryUseCase operators;

    private static ClassifiedVehiclePosition snapshot() {
        VehiclePosition position = VehiclePosition.builder()
                .vehicleId("A12345")
                .serviceCode("100")
                .coordinates(new Coordinates(new BigDecimal("-22.9"), new BigDecimal("-43.2")))
                .speed(30.0)
                .positionTimestamp(Instant.parse("2026-07-22T12:00:00Z"))
                .sentTimestamp(Instant.parse("2026-07-22T12:00:02Z"))
                .receivedAt(Instant.parse("2026-07-22T12:00:03Z"))
                .source(PositionSource.DADOS_MOBILIDADE_RIO)
                .build();
        return new ClassifiedVehiclePosition(position,
                PositionClassification.from(VehiclePositionStatus.IN_OPERATION, Set.of()));
    }

    @Test
    void shouldReturnCurrentPositionsWithOperatorLabel() throws Exception {
        when(useCase.getCurrent(any())).thenReturn(List.of(snapshot()));
        when(operators.resolve("A12345")).thenReturn(Optional.of(new Operator("A", "Consórcio Intersul")));

        mockMvc.perform(get("/api/v1/vehicle-positions/current"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$[0].vehicleId").value("A12345"))
                .andExpect(jsonPath("$[0].serviceCode").value("100"))
                .andExpect(jsonPath("$[0].classificationStatus").value("IN_OPERATION"))
                .andExpect(jsonPath("$[0].valid").value(true))
                .andExpect(jsonPath("$[0].latitude").value(-22.9))
                .andExpect(jsonPath("$[0].operatorName").value("Consórcio Intersul"));
    }

    @Test
    void shouldReturnEmptyArrayWhenNoPositions() throws Exception {
        when(useCase.getCurrent(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vehicle-positions/current"))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void shouldPassFiltersToUseCase() throws Exception {
        when(useCase.getCurrent(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/vehicle-positions/current")
                        .param("serviceCode", "100")
                        .param("routeId", "R1")
                        .param("classificationStatus", "IN_GARAGE"))
                .andExpect(status().isOk());

        verify(useCase).getCurrent(eq(new VehiclePositionFilter("100", "R1", VehiclePositionStatus.IN_GARAGE)));
    }

    @Test
    void shouldReturnProblemJsonForInvalidClassificationStatus() throws Exception {
        mockMvc.perform(get("/api/v1/vehicle-positions/current")
                        .param("classificationStatus", "NOT_A_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400));
    }
}

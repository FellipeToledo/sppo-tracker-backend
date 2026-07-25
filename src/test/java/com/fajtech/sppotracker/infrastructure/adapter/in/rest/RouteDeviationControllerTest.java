package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.GetRouteDeviationHistoryUseCase;
import com.fajtech.sppotracker.application.route.RouteDeviationHistoryEntry;
import com.fajtech.sppotracker.application.route.RouteDeviationQuery;
import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.DeviationSeverity;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RouteDeviationController.class)
class RouteDeviationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetRouteDeviationHistoryUseCase useCase;

    @Test
    void shouldReturnHistoryWithFilters() throws Exception {
        RouteDeviationHistoryEntry entry = new RouteDeviationHistoryEntry(
                7L, "A1001", "100", null, DeviationEventType.CONFIRMED, DeviationSeverity.MEDIO,
                220.0, Instant.parse("2026-07-24T12:00:00Z"));
        when(useCase.recent(any())).thenReturn(List.of(entry));

        mockMvc.perform(get("/api/v1/route-deviations")
                        .param("serviceCode", "100").param("type", "CONFIRMED").param("limit", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].vehicleId").value("A1001"))
                .andExpect(jsonPath("$[0].type").value("CONFIRMED"))
                .andExpect(jsonPath("$[0].severity").value("MEDIO"))
                .andExpect(jsonPath("$[0].distanceMeters").value(220.0));

        ArgumentCaptor<RouteDeviationQuery> captor = ArgumentCaptor.forClass(RouteDeviationQuery.class);
        org.mockito.Mockito.verify(useCase).recent(captor.capture());
        RouteDeviationQuery query = captor.getValue();
        assertThat(query.serviceCode()).isEqualTo("100");
        assertThat(query.type()).isEqualTo(DeviationEventType.CONFIRMED);
        assertThat(query.limit()).isEqualTo(50);
    }

    @Test
    void shouldRejectInvalidEnumWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/route-deviations").param("type", "NOPE"))
                .andExpect(status().isBadRequest());
    }
}

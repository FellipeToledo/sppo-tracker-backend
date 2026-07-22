package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;
import com.fajtech.sppotracker.application.port.in.GetGpsPollingStatusUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GpsPollingStatusController.class)
class GpsPollingStatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetGpsPollingStatusUseCase useCase;

    @Test
    void shouldReturnStatusWhenPresent() throws Exception {
        PollingCycleResult result = PollingCycleResult.success(
                Instant.parse("2026-07-22T11:58:30Z"), Instant.parse("2026-07-22T12:00:00Z"),
                42, Instant.parse("2026-07-22T12:00:00Z"), Duration.ofMillis(15));
        when(useCase.lastStatus()).thenReturn(Optional.of(result));

        mockMvc.perform(get("/api/v1/gps-polling/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("SUCCESS"))
                .andExpect(jsonPath("$.receivedCount").value(42));
    }

    @Test
    void shouldReturnNoContentWhenNoCycleYet() throws Exception {
        when(useCase.lastStatus()).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/gps-polling/status"))
                .andExpect(status().isNoContent());
    }
}

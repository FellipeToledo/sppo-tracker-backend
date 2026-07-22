package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.infrastructure.config.DadosRioProviderProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Testes do adapter WebClient contra a API pública da SMTR (MockWebServer). */
class DadosMobilidadeRioGpsProviderTest {

    private static final Instant FROM = Instant.parse("2026-07-22T12:00:00Z"); // 09:00 BRT
    private static final Instant TO = Instant.parse("2026-07-22T12:01:30Z");   // 09:01:30 BRT

    private MockWebServer server;
    private DadosMobilidadeRioGpsProvider provider;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();

        DadosRioProviderProperties props = new DadosRioProviderProperties(
                server.url("/").toString().replaceAll("/$", ""),
                "/gps/sppo",
                "America/Sao_Paulo",
                Duration.ofSeconds(2),
                1024 * 1024,
                2,
                Duration.ofMillis(1));
        DadosMobilidadeRioGpsMapper mapper =
                new DadosMobilidadeRioGpsMapper(Clock.fixed(Instant.parse("2026-07-22T12:00:05Z"), ZoneOffset.UTC));
        provider = new DadosMobilidadeRioGpsProvider(WebClient.builder(), props, mapper);
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    @Test
    void shouldFetchAndMapPositions() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          {"ordem":"A12345","latitude":"-22,89206","longitude":"-43,17654",
                           "datahora":"1690000000000","velocidade":"23,5","linha":"100",
                           "datahoraenvio":"1690000002000","datahoraservidor":"1690000005000"}
                        ]"""));

        List<VehiclePosition> result = provider.fetch(FROM, TO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vehicleId()).isEqualTo("A12345");
        assertThat(result.get(0).serviceCode()).isEqualTo("100");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/gps/sppo");
        assertThat(request.getRequestUrl().queryParameter("dataInicial")).isEqualTo("2026-07-22 09:00:00");
        assertThat(request.getRequestUrl().queryParameter("dataFinal")).isEqualTo("2026-07-22 09:01:30");
    }

    @Test
    void shouldKeepGoodItemsWhenOneIsMalformed() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        [
                          {"ordem":"A1","latitude":"-22,9","longitude":"-43,1",
                           "datahora":"1690000000000","velocidade":"10","linha":"100",
                           "datahoraenvio":"1690000002000","datahoraservidor":"1690000005000"},
                          {"ordem":"A2","latitude":"abc","longitude":"-43,1",
                           "datahora":"1690000000000","velocidade":"10","linha":"100",
                           "datahoraenvio":"1690000002000","datahoraservidor":"1690000005000"}
                        ]"""));

        List<VehiclePosition> result = provider.fetch(FROM, TO);

        assertThat(result).extracting(VehiclePosition::vehicleId).containsExactly("A1");
    }

    @Test
    void shouldReturnEmptyListForEmptyResponse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody("[]"));

        assertThat(provider.fetch(FROM, TO)).isEmpty();
    }

    @Test
    void shouldRetryOnServerErrorAndFailWhenExhausted() {
        // retry-max-attempts=2 → 3 tentativas totais
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() -> provider.fetch(FROM, TO)).isInstanceOf(RuntimeException.class);
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    void shouldNotRetryOnBadRequest() {
        server.enqueue(new MockResponse().setResponseCode(400));

        assertThatThrownBy(() -> provider.fetch(FROM, TO)).isInstanceOf(RuntimeException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}

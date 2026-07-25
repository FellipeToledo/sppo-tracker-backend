package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.infrastructure.config.DadosRioProviderProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import tools.jackson.databind.json.JsonMapper;

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
    private SimpleMeterRegistry registry;
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
        registry = new SimpleMeterRegistry();
        provider = new DadosMobilidadeRioGpsProvider(
                WebClient.builder(), props, mapper, JsonMapper.builder().build(), registry);
    }

    private double requests(String outcome) {
        return registry.get("gps.provider.requests").tag("outcome", outcome).counter().count();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.close();
    }

    @Test
    void shouldFetchAndMapPositions() throws InterruptedException {
        server.enqueue(new MockResponse.Builder()
                .setHeader("Content-Type", "application/json")
                .body("""
                        [
                          {"ordem":"A12345","latitude":"-22,89206","longitude":"-43,17654",
                           "datahora":"1690000000000","velocidade":"23,5","linha":"100",
                           "datahoraenvio":"1690000002000","datahoraservidor":"1690000005000"}
                        ]""").build());

        List<VehiclePosition> result = provider.fetch(FROM, TO);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).vehicleId()).isEqualTo("A12345");
        assertThat(result.get(0).serviceCode()).isEqualTo("100");

        RecordedRequest request = server.takeRequest();
        assertThat(request.getUrl().encodedPath()).isEqualTo("/gps/sppo");
        assertThat(request.getUrl().queryParameter("dataInicial")).isEqualTo("2026-07-22 09:00:00");
        assertThat(request.getUrl().queryParameter("dataFinal")).isEqualTo("2026-07-22 09:01:30");

        // métricas: sucesso, timer e janela (90s) registrados
        assertThat(requests("success")).isEqualTo(1.0);
        assertThat(registry.get("gps.provider.request.duration").timer().count()).isEqualTo(1L);
        assertThat(registry.get("gps.provider.window.seconds").summary().totalAmount()).isEqualTo(90.0);
    }

    @Test
    void shouldKeepGoodItemsWhenOneIsMalformed() {
        server.enqueue(new MockResponse.Builder()
                .setHeader("Content-Type", "application/json")
                .body("""
                        [
                          {"ordem":"A1","latitude":"-22,9","longitude":"-43,1",
                           "datahora":"1690000000000","velocidade":"10","linha":"100",
                           "datahoraenvio":"1690000002000","datahoraservidor":"1690000005000"},
                          {"ordem":"A2","latitude":"abc","longitude":"-43,1",
                           "datahora":"1690000000000","velocidade":"10","linha":"100",
                           "datahoraenvio":"1690000002000","datahoraservidor":"1690000005000"}
                        ]""").build());

        List<VehiclePosition> result = provider.fetch(FROM, TO);

        assertThat(result).extracting(VehiclePosition::vehicleId).containsExactly("A1");
    }

    @Test
    void shouldReturnEmptyListForEmptyResponse() {
        server.enqueue(new MockResponse.Builder()
                .setHeader("Content-Type", "application/json")
                .body("[]").build());

        assertThat(provider.fetch(FROM, TO)).isEmpty();
    }

    @Test
    void shouldParseJsonEvenWhenContentTypeIsTextHtml() {
        // A SMTR frequentemente devolve JSON válido rotulado como text/html.
        server.enqueue(new MockResponse.Builder()
                .setHeader("Content-Type", "text/html;charset=utf-8")
                .body("""
                        [
                          {"ordem":"B7","latitude":"-22,9","longitude":"-43,1",
                           "datahora":"1690000000000","velocidade":"10","linha":"483",
                           "datahoraenvio":"1690000002000","datahoraservidor":"1690000005000"}
                        ]""").build());

        List<VehiclePosition> result = provider.fetch(FROM, TO);

        assertThat(result).extracting(VehiclePosition::vehicleId).containsExactly("B7");
        assertThat(requests("success")).isEqualTo(1.0);
    }

    @Test
    void shouldRetryHtmlErrorPageAndFailWhenExhausted() {
        // Página de erro (ex.: "Service Unavailable") com HTTP 200 e text/html: tratada
        // como falha transitória → re-tentada (retry-max-attempts=2 → 3 tentativas).
        String errorPage = "<html><head><title>Service Unavailable</title></head>"
                + "<body><h2>Service Unavailable</h2></body></html>";
        for (int i = 0; i < 3; i++) {
            server.enqueue(new MockResponse.Builder()
                    .setHeader("Content-Type", "text/html;charset=utf-8")
                    .body(errorPage).build());
        }

        assertThatThrownBy(() -> provider.fetch(FROM, TO)).isInstanceOf(RuntimeException.class);
        assertThat(server.getRequestCount()).isEqualTo(3);
        assertThat(requests("failure")).isEqualTo(1.0);
        assertThat(requests("success")).isZero();
    }

    @Test
    void shouldRecoverWhenHtmlErrorPageIsFollowedByValidJson() {
        // Primeira resposta é a página HTML de erro (transitória); o retry alcança JSON.
        server.enqueue(new MockResponse.Builder()
                .setHeader("Content-Type", "text/html;charset=utf-8")
                .body("<html><body>Service Unavailable</body></html>").build());
        server.enqueue(new MockResponse.Builder()
                .setHeader("Content-Type", "text/html;charset=utf-8")
                .body("""
                        [
                          {"ordem":"C9","latitude":"-22,9","longitude":"-43,1",
                           "datahora":"1690000000000","velocidade":"10","linha":"550",
                           "datahoraenvio":"1690000002000","datahoraservidor":"1690000005000"}
                        ]""").build());

        List<VehiclePosition> result = provider.fetch(FROM, TO);

        assertThat(result).extracting(VehiclePosition::vehicleId).containsExactly("C9");
        assertThat(server.getRequestCount()).isEqualTo(2);
        assertThat(requests("success")).isEqualTo(1.0);
    }

    @Test
    void shouldRetryOnServerErrorAndFailWhenExhausted() {
        // retry-max-attempts=2 → 3 tentativas totais
        server.enqueue(new MockResponse.Builder().code(500).build());
        server.enqueue(new MockResponse.Builder().code(500).build());
        server.enqueue(new MockResponse.Builder().code(500).build());

        assertThatThrownBy(() -> provider.fetch(FROM, TO)).isInstanceOf(RuntimeException.class);
        assertThat(server.getRequestCount()).isEqualTo(3);
        assertThat(requests("failure")).isEqualTo(1.0);
        assertThat(requests("success")).isZero();
    }

    @Test
    void shouldNotRetryOnBadRequest() {
        server.enqueue(new MockResponse.Builder().code(400).build());

        assertThatThrownBy(() -> provider.fetch(FROM, TO)).isInstanceOf(RuntimeException.class);
        assertThat(server.getRequestCount()).isEqualTo(1);
    }
}

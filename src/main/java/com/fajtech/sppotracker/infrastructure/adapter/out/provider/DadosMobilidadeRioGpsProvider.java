package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import com.fajtech.sppotracker.application.port.out.FetchExternalGpsPositionsPort;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import com.fajtech.sppotracker.infrastructure.config.DadosRioProviderProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.TimeoutException;

/**
 * Adapter de saída que consome a API pública de GPS do SPPO (SMTR) via
 * {@link WebClient} e implementa {@link FetchExternalGpsPositionsPort}.
 *
 * <p>Comportamento (docs/regras-de-negocio.md §1, §3.1):
 * <ul>
 *   <li>a janela UTC é convertida para o fuso configurado (BRT) e formatada
 *       {@code yyyy-MM-dd HH:mm:ss} nos filtros {@code dataInicial}/{@code dataFinal};</li>
 *   <li>timeout por chamada; retry com backoff <b>apenas</b> para falhas
 *       transitórias (timeout, HTTP 5xx, HTTP 429) — 400/payload inválido não têm retry;</li>
 *   <li>itens malformados são descartados individualmente pelo mapper.</li>
 * </ul>
 */
@Component
public class DadosMobilidadeRioGpsProvider implements FetchExternalGpsPositionsPort {

    private static final DateTimeFormatter FILTER_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int TOO_MANY_REQUESTS = 429;
    private static final ParameterizedTypeReference<List<DadosMobilidadeRioGpsItem>> ITEM_LIST =
            new ParameterizedTypeReference<>() {
            };

    private final WebClient webClient;
    private final DadosRioProviderProperties properties;
    private final DadosMobilidadeRioGpsMapper mapper;
    private final DateTimeFormatter filterFormatter;

    private final Counter requestsSuccess;
    private final Counter requestsFailure;
    private final Timer requestDuration;
    private final DistributionSummary windowSeconds;

    public DadosMobilidadeRioGpsProvider(WebClient.Builder webClientBuilder,
                                         DadosRioProviderProperties properties,
                                         DadosMobilidadeRioGpsMapper mapper,
                                         MeterRegistry registry) {
        this.properties = properties;
        this.mapper = mapper;
        this.filterFormatter = FILTER_FORMAT.withZone(properties.zoneId());
        ExchangeStrategies strategies = ExchangeStrategies.builder()
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(properties.maxInMemorySize()))
                .build();
        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .exchangeStrategies(strategies)
                .build();
        this.requestsSuccess = Counter.builder("gps.provider.requests").tag("outcome", "success").register(registry);
        this.requestsFailure = Counter.builder("gps.provider.requests").tag("outcome", "failure").register(registry);
        this.requestDuration = Timer.builder("gps.provider.request.duration")
                .description("Duração da chamada ao provider (incl. retries)").register(registry);
        this.windowSeconds = DistributionSummary.builder("gps.provider.window.seconds")
                .description("Amplitude da janela solicitada ao provider").baseUnit("seconds").register(registry);
    }

    @Override
    public List<VehiclePosition> fetch(Instant from, Instant to) {
        windowSeconds.record(Duration.between(from, to).toSeconds());
        Timer.Sample sample = Timer.start();
        try {
            List<DadosMobilidadeRioGpsItem> items = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path(properties.path())
                            .queryParam("dataInicial", filterFormatter.format(from))
                            .queryParam("dataFinal", filterFormatter.format(to))
                            .build())
                    .retrieve()
                    .bodyToMono(ITEM_LIST)
                    .timeout(properties.requestTimeout())
                    .retryWhen(Retry.backoff(properties.retryMaxAttempts(), properties.retryBackoff())
                            .filter(DadosMobilidadeRioGpsProvider::isTransient))
                    .blockOptional()
                    .orElseGet(List::of);
            requestsSuccess.increment();
            return mapper.mapAll(items);
        } catch (RuntimeException e) {
            requestsFailure.increment();
            throw e;
        } finally {
            sample.stop(requestDuration);
        }
    }

    /** Transitório = timeout, erro de conexão, HTTP 5xx ou HTTP 429 (§3.1). */
    private static boolean isTransient(Throwable throwable) {
        if (throwable instanceof WebClientResponseException response) {
            return response.getStatusCode().is5xxServerError()
                    || response.getStatusCode().value() == TOO_MANY_REQUESTS;
        }
        return throwable instanceof TimeoutException
                || throwable instanceof WebClientRequestException;
    }
}

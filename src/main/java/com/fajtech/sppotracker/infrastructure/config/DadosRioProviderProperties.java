package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Configuração do provider da API pública da SMTR
 * (docs/regras-de-negocio.md §1, §8). Vinculado a {@code gps.provider.dados-rio}.
 *
 * @param baseUrl          base da API (ex.: {@code https://dados.mobilidade.rio})
 * @param path             caminho do recurso de GPS (ex.: {@code /gps/sppo})
 * @param requestTimeZone  fuso do filtro {@code dataInicial}/{@code dataFinal} (BRT)
 * @param requestTimeout   timeout de cada chamada ao provider
 * @param maxInMemorySize  limite de buffer da resposta (bytes)
 * @param retryMaxAttempts nº de retentativas para falhas transitórias (timeout/5xx/429)
 * @param retryBackoff     backoff base entre retentativas
 */
@ConfigurationProperties(prefix = "gps.provider.dados-rio")
public record DadosRioProviderProperties(
        String baseUrl,
        String path,
        String requestTimeZone,
        Duration requestTimeout,
        int maxInMemorySize,
        int retryMaxAttempts,
        Duration retryBackoff) {

    public ZoneId zoneId() {
        return ZoneId.of(requestTimeZone);
    }
}

package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração do polling de GPS (docs/regras-de-negocio.md §3.1, §8), vinculada a
 * {@code gps.polling}. Demais chaves sob esse prefixo (ex.: {@code request-timeout},
 * {@code deduplication-ttl}) pertencem a outros componentes e são ignoradas aqui.
 *
 * @param enabled                  liga/desliga o polling
 * @param fixedDelay               intervalo entre ciclos (usado pelo scheduler)
 * @param overlapWindow            janela de sobreposição (> fixedDelay)
 * @param failureCooldownThreshold falhas consecutivas para entrar em cooldown
 * @param failureCooldown          duração do cooldown
 */
@ConfigurationProperties(prefix = "gps.polling")
public record GpsPollingProperties(
        boolean enabled,
        Duration fixedDelay,
        Duration overlapWindow,
        int failureCooldownThreshold,
        Duration failureCooldown) {
}

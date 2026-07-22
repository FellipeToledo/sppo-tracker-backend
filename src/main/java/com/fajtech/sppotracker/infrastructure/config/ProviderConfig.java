package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;

/**
 * Wiring da infraestrutura do provider: habilita as propriedades, fornece um
 * {@link Clock} (UTC) injetável e um {@code WebClient.Builder} para o adapter.
 */
@Configuration
@EnableConfigurationProperties(DadosRioProviderProperties.class)
public class ProviderConfig {

    /** Clock em UTC — injetável para tornar o tempo testável (tempo interno em UTC). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Builder do WebClient para o provider. Fornecido explicitamente — no Spring
     * Boot 4 a auto-configuração do {@code WebClient.Builder} não está garantida
     * numa aplicação servlet; {@link ConditionalOnMissingBean} cede se houver.
     */
    @Bean
    @ConditionalOnMissingBean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

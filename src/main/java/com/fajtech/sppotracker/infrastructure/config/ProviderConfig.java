package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring da infraestrutura do provider: habilita as propriedades e fornece um
 * {@link Clock} (UTC) injetável. O {@code WebClient.Builder} usado pelo adapter
 * vem da auto-configuração do Spring Boot (webflux no classpath).
 */
@Configuration
@EnableConfigurationProperties(DadosRioProviderProperties.class)
public class ProviderConfig {

    /** Clock em UTC — injetável para tornar o tempo testável (tempo interno em UTC). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}

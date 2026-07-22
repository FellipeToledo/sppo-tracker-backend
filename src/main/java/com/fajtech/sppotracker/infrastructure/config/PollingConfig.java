package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.application.polling.GpsPollingService;
import com.fajtech.sppotracker.application.port.out.FetchExternalGpsPositionsPort;
import com.fajtech.sppotracker.application.port.out.ProviderReadinessPort;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Wiring do caso de uso de polling. Mantém a camada de aplicação livre de
 * framework: a infraestrutura instancia o {@link GpsPollingService} a partir das
 * {@link GpsPollingProperties}.
 */
@Configuration
@EnableConfigurationProperties(GpsPollingProperties.class)
public class PollingConfig {

    @Bean
    public GpsPollingService gpsPollingService(FetchExternalGpsPositionsPort fetchPort,
                                               ProviderReadinessPort readinessPort,
                                               Clock clock,
                                               GpsPollingProperties properties) {
        return new GpsPollingService(
                fetchPort,
                readinessPort,
                clock,
                properties.overlapWindow(),
                properties.failureCooldownThreshold(),
                properties.failureCooldown());
    }
}

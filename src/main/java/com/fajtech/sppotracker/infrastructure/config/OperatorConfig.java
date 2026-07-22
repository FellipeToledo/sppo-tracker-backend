package com.fajtech.sppotracker.infrastructure.config;

import com.fajtech.sppotracker.application.operator.OperatorService;
import com.fajtech.sppotracker.application.port.out.OperatorDirectoryPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring do caso de uso de operadoras (docs/regras-de-negocio.md §6). Mantém a
 * aplicação livre de framework.
 */
@Configuration
public class OperatorConfig {

    @Bean
    public OperatorService operatorService(OperatorDirectoryPort directory) {
        return new OperatorService(directory);
    }
}

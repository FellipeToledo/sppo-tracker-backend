package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import com.fajtech.sppotracker.application.port.out.ProviderReadinessPort;
import org.springframework.stereotype.Component;

/**
 * Readiness da API pública da SMTR: sempre pronta, pois não há credencial a
 * validar (docs/regras-de-negocio.md §3.1).
 */
@Component
public class PublicApiProviderReadiness implements ProviderReadinessPort {

    @Override
    public boolean isReady() {
        return true;
    }
}

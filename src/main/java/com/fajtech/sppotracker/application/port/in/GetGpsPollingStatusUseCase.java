package com.fajtech.sppotracker.application.port.in;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;

import java.util.Optional;

/**
 * Porta de entrada: expõe o status do último ciclo de polling
 * (docs/regras-de-negocio.md §7.1).
 */
public interface GetGpsPollingStatusUseCase {

    /** Último resultado de ciclo; vazio se nenhum ciclo rodou ainda. */
    Optional<PollingCycleResult> lastStatus();
}

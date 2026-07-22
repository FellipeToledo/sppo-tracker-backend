package com.fajtech.sppotracker.application.port.in;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;

/**
 * Porta de entrada: executa um ciclo de polling de GPS (buscar janela no provider,
 * respeitando readiness e cooldown). Disparado pelo scheduler
 * (docs/regras-de-negocio.md §3).
 */
public interface RunGpsPollingCycleUseCase {

    PollingCycleResult runCycle();
}

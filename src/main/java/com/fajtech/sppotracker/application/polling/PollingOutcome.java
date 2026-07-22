package com.fajtech.sppotracker.application.polling;

/** Desfecho de um ciclo de polling (docs/regras-de-negocio.md §3.1). */
public enum PollingOutcome {
    /** Ciclo executou e buscou posições no provider. */
    SUCCESS,
    /** Ciclo chamou o provider mas houve falha. */
    FAILURE,
    /** Ciclo não chamou o provider (ver {@link PollingSkipReason}). */
    SKIPPED
}

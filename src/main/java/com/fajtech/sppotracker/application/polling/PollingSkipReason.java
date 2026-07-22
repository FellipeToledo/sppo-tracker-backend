package com.fajtech.sppotracker.application.polling;

/** Motivo pelo qual um ciclo de polling foi pulado (docs/regras-de-negocio.md §3.1). */
public enum PollingSkipReason {
    /** A fonte externa não está pronta para ser consultada. */
    PROVIDER_NOT_READY,
    /** Em cooldown após falhas consecutivas — o provider não é chamado. */
    FAILURE_COOLDOWN
}

package com.fajtech.sppotracker.application.port.out;

/**
 * Porta de saída que indica se a fonte externa de GPS está pronta para ser
 * consultada. O scheduler só faz polling quando a fonte está pronta
 * (docs/regras-de-negocio.md §3.1). Para a API pública da SMTR, está sempre
 * pronta (não há credencial); a abstração existe para permitir outras fontes.
 */
public interface ProviderReadinessPort {

    boolean isReady();
}

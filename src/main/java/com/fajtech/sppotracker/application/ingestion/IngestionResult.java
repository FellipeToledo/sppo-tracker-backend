package com.fajtech.sppotracker.application.ingestion;

/**
 * Contadores de um lote processado pelo hot path (docs/regras-de-negocio.md §3).
 *
 * @param received   posições recebidas do provider
 * @param duplicated posições descartadas por deduplicação
 * @param unchanged  posições descartadas por não representarem mudança
 * @param changed    posições que mudaram e foram salvas no snapshot
 */
public record IngestionResult(int received, int duplicated, int unchanged, int changed) {

    public static IngestionResult empty() {
        return new IngestionResult(0, 0, 0, 0);
    }
}

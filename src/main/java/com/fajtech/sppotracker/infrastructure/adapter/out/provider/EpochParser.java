package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import java.time.Instant;

/**
 * Parsing defensivo de timestamps epoch vindos do feed público. Os valores
 * chegam em Unix time (normalmente milissegundos), mas para robustez toleramos
 * segundos <b>ou</b> milissegundos: valores {@code < 1e11} são interpretados
 * como segundos, os demais como milissegundos. Entrada nula, vazia ou inválida
 * retorna {@code null}. Ver docs/regras-de-negocio.md §1.
 */
final class EpochParser {

    /** Limiar da heurística s/ms: {@code < 1e11} → segundos, senão milissegundos. */
    private static final long SECONDS_MILLIS_THRESHOLD = 100_000_000_000L;

    private EpochParser() {
    }

    static Instant parse(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        long value;
        try {
            value = Long.parseLong(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
        return Math.abs(value) < SECONDS_MILLIS_THRESHOLD
                ? Instant.ofEpochSecond(value)
                : Instant.ofEpochMilli(value);
    }
}

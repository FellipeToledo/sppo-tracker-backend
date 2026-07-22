package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import java.math.BigDecimal;

/**
 * Parsing defensivo de valores decimais vindos do feed público, que usam
 * <b>vírgula</b> decimal (ex.: {@code -22,89206}). Aceita vírgula e ponto.
 * Entrada nula, vazia ou inválida retorna {@code null} (registro descartado
 * a jusante). Ver docs/regras-de-negocio.md §1.
 */
final class DecimalParser {

    private DecimalParser() {
    }

    static BigDecimal parse(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().replace(',', '.');
        if (normalized.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(normalized);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

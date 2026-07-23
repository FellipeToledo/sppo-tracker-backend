package com.fajtech.sppotracker.domain.route;

/**
 * Normalização de código de linha — <b>contrato compartilhado</b> com o
 * {@code sppo-gtfs-service} (ver {@code LineCode} lá). Deve ser idêntica dos dois
 * lados, senão a junção {@code linha → shapes} falha silenciosamente (fonte nº 1 de
 * bugs na integração).
 *
 * <p>Algoritmo:
 * <ol>
 *   <li>{@code trim} + {@code toUpperCase};</li>
 *   <li>se o valor for puramente numérico, remove zeros à esquerda ({@code 0100 ≡ 100}).</li>
 * </ol>
 *
 * <p>Aqui a chave normalizada é usada para <b>indexar o cache local</b> de geometria;
 * o serviço GTFS faz a junção autoritativa contra {@code servico}.
 */
public final class LineCodeKey {

    private LineCodeKey() {
    }

    /** Devolve a chave normalizada, ou {@code null} se a entrada for nula/vazia. */
    public static String of(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase();
        if (normalized.isEmpty()) {
            return null;
        }
        return stripLeadingZerosIfNumeric(normalized);
    }

    private static String stripLeadingZerosIfNumeric(String normalized) {
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (c < '0' || c > '9') {
                return normalized;
            }
        }
        int start = 0;
        while (start < normalized.length() - 1 && normalized.charAt(start) == '0') {
            start++;
        }
        return normalized.substring(start);
    }
}

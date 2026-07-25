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
        // Locale.ROOT: normalização independente do locale do host (evita, p.ex., o
        // 'i' → 'İ' do locale turco, que quebraria o contrato compartilhado com o gtfs).
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        return stripLeadingZerosIfNumeric(normalized);
    }

    /**
     * True se o código <b>pode</b> ser uma linha, e portanto vale pedir a geometria dela.
     *
     * <p>O campo {@code linha} do feed carrega, além de linha, <b>estado operacional</b>:
     * {@code GARAGEM}, {@code ESPECIAL}, {@code FORA DE OP}, {@code 1 GAR},
     * {@code MANUTENCAO}, {@code TREINO}, {@code RESERVADO}, {@code OPER.}, {@code 00000}.
     * Esses nunca existirão no planejamento — pedi-los ao {@code sppo-gtfs-service} é I/O
     * garantidamente inútil, re-tentada a cada expiração do TTL de vazio, e polui a
     * métrica {@code gps.route.shape.resolve{result=empty}}, que deveria significar
     * "linha de verdade sem planejamento".
     *
     * <p>Critério, aplicado <b>sobre a forma normalizada</b>: sem espaço em branco, ao
     * menos um dígito, e não ser o código nulo {@code 0} (de {@code 00000}). Cobre todo
     * formato real de linha do SPPO — {@code 100}, {@code SV789}, {@code SN810},
     * {@code LECD11}, {@code SP…}, {@code SVB…}, {@code SR…}.
     *
     * <p>⚠️ Isto é um <b>pré-filtro do cliente</b>, não parte do contrato de normalização:
     * {@link #of(String)} segue idêntico ao {@code LineCode} do {@code sppo-gtfs-service}.
     * Um código que passa daqui e não existe no feed continua devolvendo vazio — e é
     * exatamente esse o sinal que queremos ver na métrica.
     */
    public static boolean looksLikeLine(String raw) {
        String normalized = of(raw);
        if (normalized == null || "0".equals(normalized)) {
            return false;
        }
        boolean hasDigit = false;
        for (int i = 0; i < normalized.length(); i++) {
            char c = normalized.charAt(i);
            if (Character.isWhitespace(c)) {
                return false;
            }
            if (c >= '0' && c <= '9') {
                hasDigit = true;
            }
        }
        return hasDigit;
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

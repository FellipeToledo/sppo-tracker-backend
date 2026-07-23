package com.fajtech.sppotracker.domain.operator;

import java.util.Locale;

/**
 * Consórcio operador de um veículo (docs/regras-de-negocio.md §6). Identificado
 * pelo primeiro caractere da ordem ({@code vehicleId}) — A, B, C ou D (ver §1:
 * formato {@code XYYZZZ}).
 *
 * <p>Value object imutável e auto-validado (padrão de {@code Coordinates}): o
 * {@code code} é normalizado para upper-case e deve ter exatamente um caractere.
 *
 * @param code caractere único (upper-case) que identifica o consórcio
 * @param name nome do consórcio
 */
public record Consortium(String code, String name) {

    public Consortium {
        code = requireLength(requireNonBlank(code, "code"), 1, "code");
        name = requireNonBlank(name, "name");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireLength(String value, int length, String field) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (normalized.length() != length) {
            throw new IllegalArgumentException(field + " must have exactly " + length + " character(s)");
        }
        return normalized;
    }
}

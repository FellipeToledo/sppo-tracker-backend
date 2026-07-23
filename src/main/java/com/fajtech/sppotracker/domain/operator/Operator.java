package com.fajtech.sppotracker.domain.operator;

import java.util.Locale;

/**
 * Consórcio operador de um veículo (docs/regras-de-negocio.md §6). Resolvido pelo
 * primeiro caractere da ordem ({@code vehicleId}), que identifica o consórcio
 * (A–D; ver §1: formato {@code XYYZZZ}).
 *
 * <p>Value object imutável e auto-validado (padrão de {@code Coordinates}): o
 * {@code prefix} é normalizado para upper-case e deve ter exatamente um
 * caractere — invariante que garante que a chave sempre casa com o lookup de 1
 * caractere do de-para (evita entrada carregada mas nunca resolvível).
 *
 * @param prefix caractere único (upper-case) que identifica o consórcio
 * @param name   nome do consórcio operador
 */
public record Operator(String prefix, String name) {

    public Operator {
        prefix = requireSingleChar(requireNonBlank(prefix, "prefix"));
        name = requireNonBlank(name, "name");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireSingleChar(String prefix) {
        String normalized = prefix.toUpperCase(Locale.ROOT);
        if (normalized.length() != 1) {
            throw new IllegalArgumentException("prefix must be a single character");
        }
        return normalized;
    }
}

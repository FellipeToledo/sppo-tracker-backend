package com.fajtech.sppotracker.domain.operator;

import java.util.Objects;

/**
 * Consórcio operador de um veículo (docs/regras-de-negocio.md §6). Resolvido pelo
 * primeiro caractere da ordem ({@code vehicleId}), que identifica o consórcio
 * (A–D; ver §1: formato {@code XYYZZZ}).
 *
 * @param prefix primeiro caractere (upper-case) da ordem — A, B, C ou D
 * @param name   nome do consórcio operador
 */
public record Operator(String prefix, String name) {

    public Operator {
        prefix = requireNonBlank(prefix, "prefix");
        name = requireNonBlank(name, "name");
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}

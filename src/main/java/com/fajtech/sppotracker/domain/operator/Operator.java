package com.fajtech.sppotracker.domain.operator;

import java.util.Objects;

/**
 * Operadora (empresa) de um veículo (docs/regras-de-negocio.md §6). Resolvida pelo
 * prefixo da ordem (4 primeiros caracteres do {@code vehicleId}).
 *
 * @param prefix prefixo de 4 caracteres (upper-case) da ordem
 * @param name   nome da empresa operadora
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

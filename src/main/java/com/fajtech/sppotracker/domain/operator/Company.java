package com.fajtech.sppotracker.domain.operator;

import java.util.Locale;

/**
 * Empresa operadora de um veículo (docs/regras-de-negocio.md §6). Identificada
 * pelos quatro primeiros caracteres da ordem ({@code vehicleId}) — a letra do
 * consórcio seguida de três dígitos (ver §1: formato {@code XYYZZZ}).
 *
 * <p>Value object imutável e auto-validado (padrão de {@code Coordinates}): o
 * {@code prefix} é normalizado para upper-case e deve ter exatamente quatro
 * caracteres — invariante que garante que a chave sempre casa com o lookup de
 * quatro caracteres do de-para.
 *
 * @param prefix prefixo de quatro caracteres (upper-case): letra do consórcio + 3 dígitos
 * @param name   nome da empresa operadora
 */
public record Company(String prefix, String name) {

    private static final int PREFIX_LENGTH = 4;

    public Company {
        prefix = requireLength(requireNonBlank(prefix, "prefix"));
        name = requireNonBlank(name, "name");
    }

    /** Letra do consórcio a que a empresa pertence (primeiro caractere do prefixo). */
    public String consortiumCode() {
        return prefix.substring(0, 1);
    }

    private static String requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }

    private static String requireLength(String prefix) {
        String normalized = prefix.toUpperCase(Locale.ROOT);
        if (normalized.length() != PREFIX_LENGTH) {
            throw new IllegalArgumentException("prefix must have exactly " + PREFIX_LENGTH + " characters");
        }
        return normalized;
    }
}

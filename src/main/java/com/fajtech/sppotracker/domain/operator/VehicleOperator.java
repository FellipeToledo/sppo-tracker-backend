package com.fajtech.sppotracker.domain.operator;

/**
 * Resultado da resolução de operadora de um veículo (docs/regras-de-negocio.md §6):
 * o {@link Consortium} (pela letra da ordem) e a {@link Company} (pelo prefixo de
 * quatro caracteres). Qualquer um pode ser {@code null} quando não resolvido — a
 * empresa tem cobertura parcial, enquanto o consórcio cobre toda a frota A–D.
 *
 * @param consortium consórcio resolvido, ou {@code null}
 * @param company    empresa resolvida, ou {@code null}
 */
public record VehicleOperator(Consortium consortium, Company company) {

    public String consortiumCode() {
        return consortium == null ? null : consortium.code();
    }

    public String consortiumName() {
        return consortium == null ? null : consortium.name();
    }

    public String companyPrefix() {
        return company == null ? null : company.prefix();
    }

    public String companyName() {
        return company == null ? null : company.name();
    }
}

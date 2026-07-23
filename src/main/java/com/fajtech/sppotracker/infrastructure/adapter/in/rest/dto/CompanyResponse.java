package com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppotracker.domain.operator.Company;
import com.fajtech.sppotracker.domain.operator.Consortium;

/**
 * Representação REST de uma empresa operadora, enriquecida com o consórcio a que
 * pertence (docs/regras-de-negocio.md §7.1).
 */
public record CompanyResponse(String prefix, String name, String consortiumCode, String consortiumName) {

    public static CompanyResponse from(Company company, Consortium consortium) {
        return new CompanyResponse(
                company.prefix(),
                company.name(),
                consortium == null ? null : consortium.code(),
                consortium == null ? null : consortium.name());
    }
}

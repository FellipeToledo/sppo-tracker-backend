package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.operator.Company;
import com.fajtech.sppotracker.domain.operator.Consortium;

import java.util.List;
import java.util.Optional;

/**
 * Porta de saída do de-para de operadoras (docs/regras-de-negocio.md §6). Reference
 * data estática, carregada uma vez fora do hot path. Resolve dois níveis: o
 * consórcio (pela letra da ordem) e a empresa (pelo prefixo de quatro caracteres).
 */
public interface OperatorDirectoryPort {

    /** Resolve o consórcio pelo primeiro caractere da ordem ({@code vehicleId}). */
    Optional<Consortium> findConsortium(String vehicleId);

    /** Resolve a empresa pelos quatro primeiros caracteres da ordem ({@code vehicleId}). */
    Optional<Company> findCompany(String vehicleId);

    /** Todos os consórcios conhecidos. */
    List<Consortium> allConsortiums();

    /** Todas as empresas conhecidas. */
    List<Company> allCompanies();
}

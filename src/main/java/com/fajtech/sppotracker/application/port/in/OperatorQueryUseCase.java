package com.fajtech.sppotracker.application.port.in;

import com.fajtech.sppotracker.domain.operator.Company;
import com.fajtech.sppotracker.domain.operator.Consortium;
import com.fajtech.sppotracker.domain.operator.VehicleOperator;

import java.util.List;

/**
 * Porta de entrada para consulta de operadoras (docs/regras-de-negocio.md §6, §7.1).
 * Expõe os dois níveis (consórcio e empresa) e a resolução por veículo.
 */
public interface OperatorQueryUseCase {

    List<Consortium> getConsortiums();

    List<Company> getCompanies();

    /** Resolve consórcio + empresa de um veículo (qualquer um pode faltar). */
    VehicleOperator resolve(String vehicleId);
}

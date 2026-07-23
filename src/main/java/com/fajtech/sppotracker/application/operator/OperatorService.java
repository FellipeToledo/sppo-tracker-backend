package com.fajtech.sppotracker.application.operator;

import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.application.port.out.OperatorDirectoryPort;
import com.fajtech.sppotracker.domain.operator.Company;
import com.fajtech.sppotracker.domain.operator.Consortium;
import com.fajtech.sppotracker.domain.operator.VehicleOperator;

import java.util.List;
import java.util.Objects;

/**
 * Consulta de operadoras (docs/regras-de-negocio.md §6). Classe pura de aplicação,
 * delega ao de-para (port out). Instanciada pela infraestrutura.
 */
public class OperatorService implements OperatorQueryUseCase {

    private final OperatorDirectoryPort directory;

    public OperatorService(OperatorDirectoryPort directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    @Override
    public List<Consortium> getConsortiums() {
        return directory.allConsortiums();
    }

    @Override
    public List<Company> getCompanies() {
        return directory.allCompanies();
    }

    @Override
    public VehicleOperator resolve(String vehicleId) {
        return new VehicleOperator(
                directory.findConsortium(vehicleId).orElse(null),
                directory.findCompany(vehicleId).orElse(null));
    }
}

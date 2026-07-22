package com.fajtech.sppotracker.application.operator;

import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.application.port.out.OperatorDirectoryPort;
import com.fajtech.sppotracker.domain.operator.Operator;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

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
    public List<Operator> getAll() {
        return directory.findAll();
    }

    @Override
    public Optional<Operator> resolve(String vehicleId) {
        return directory.findByVehicleId(vehicleId);
    }
}

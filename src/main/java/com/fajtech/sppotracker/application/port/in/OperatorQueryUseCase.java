package com.fajtech.sppotracker.application.port.in;

import com.fajtech.sppotracker.domain.operator.Operator;

import java.util.List;
import java.util.Optional;

/**
 * Porta de entrada para consulta de operadoras (docs/regras-de-negocio.md §6, §7.1).
 */
public interface OperatorQueryUseCase {

    List<Operator> getAll();

    Optional<Operator> resolve(String vehicleId);
}

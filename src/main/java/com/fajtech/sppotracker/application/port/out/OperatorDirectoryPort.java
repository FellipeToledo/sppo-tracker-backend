package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.operator.Operator;

import java.util.List;
import java.util.Optional;

/**
 * Porta de saída do de-para de operadoras (docs/regras-de-negocio.md §6).
 * Reference data estática, carregada uma vez fora do hot path.
 */
public interface OperatorDirectoryPort {

    /** Resolve o consórcio pelo primeiro caractere da ordem ({@code vehicleId}). */
    Optional<Operator> findByVehicleId(String vehicleId);

    /** Todos os consórcios conhecidos. */
    List<Operator> findAll();
}

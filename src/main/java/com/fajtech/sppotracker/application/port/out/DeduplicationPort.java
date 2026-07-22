package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;

/**
 * Porta de saída para deduplicação de posições (docs/regras-de-negocio.md §3.2).
 * A chave é {@code vehicleId : positionTimestamp : sentTimestamp}, guardada em
 * cache com TTL. Uma posição já vista dentro da janela do TTL é duplicada.
 */
public interface DeduplicationPort {

    /**
     * Registra a posição e informa se é duplicada. A operação é idempotente e
     * atômica: marca a chave se ainda não existir.
     *
     * @return {@code true} se a posição já havia sido vista (duplicada);
     *         {@code false} se é nova (foi registrada agora).
     */
    boolean isDuplicate(VehiclePosition position);
}

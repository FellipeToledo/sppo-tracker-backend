package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.DeviationSeverity;

/**
 * Porta de saída para métricas da máquina de desvio (docs/regras-de-negocio.md §5.5,
 * §7.3). Mantém a aplicação livre de framework; a implementação (Micrometer) vive na
 * infraestrutura.
 */
public interface RouteDeviationMetricsPort {

    /** Um evento de desvio emitido, contabilizado por tipo e severidade. */
    void recordEmitted(DeviationEventType type, DeviationSeverity severity);
}

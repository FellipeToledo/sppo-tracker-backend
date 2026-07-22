package com.fajtech.sppotracker.application.polling;

import java.time.Duration;
import java.time.Instant;

/**
 * Resultado imutável de um ciclo de polling. Base para o endpoint
 * {@code /api/v1/gps-polling/status} (docs/regras-de-negocio.md §7.1). Tempos em UTC.
 *
 * @param outcome             desfecho do ciclo
 * @param skipReason          motivo do skip (não-nulo apenas quando {@code outcome == SKIPPED})
 * @param windowStart         início da janela consultada (null quando pulado)
 * @param windowEnd           fim da janela consultada (null quando pulado)
 * @param receivedCount       nº de posições recebidas do provider
 * @param consecutiveFailures falhas consecutivas acumuladas após este ciclo
 * @param startedAt           instante de início do ciclo
 * @param duration            duração do ciclo
 * @param errorMessage        mensagem de erro (sem stack trace); null se não houve erro
 */
public record PollingCycleResult(
        PollingOutcome outcome,
        PollingSkipReason skipReason,
        Instant windowStart,
        Instant windowEnd,
        int receivedCount,
        int consecutiveFailures,
        Instant startedAt,
        Duration duration,
        String errorMessage) {

    public static PollingCycleResult success(Instant windowStart, Instant windowEnd,
                                             int receivedCount, Instant startedAt, Duration duration) {
        return new PollingCycleResult(PollingOutcome.SUCCESS, null, windowStart, windowEnd,
                receivedCount, 0, startedAt, duration, null);
    }

    public static PollingCycleResult failure(Instant windowStart, Instant windowEnd,
                                             int consecutiveFailures, Instant startedAt,
                                             Duration duration, String errorMessage) {
        return new PollingCycleResult(PollingOutcome.FAILURE, null, windowStart, windowEnd,
                0, consecutiveFailures, startedAt, duration, errorMessage);
    }

    public static PollingCycleResult skipped(PollingSkipReason reason, int consecutiveFailures,
                                             Instant startedAt, Duration duration) {
        return new PollingCycleResult(PollingOutcome.SKIPPED, reason, null, null,
                0, consecutiveFailures, startedAt, duration, null);
    }
}

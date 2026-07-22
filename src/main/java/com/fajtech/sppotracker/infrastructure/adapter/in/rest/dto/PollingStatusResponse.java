package com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppotracker.application.polling.PollingCycleResult;
import com.fajtech.sppotracker.application.polling.PollingOutcome;
import com.fajtech.sppotracker.application.polling.PollingSkipReason;

import java.time.Duration;
import java.time.Instant;

/**
 * Representação REST do status do último ciclo de polling
 * (docs/regras-de-negocio.md §7.1).
 */
public record PollingStatusResponse(
        PollingOutcome outcome,
        PollingSkipReason skipReason,
        Instant windowStart,
        Instant windowEnd,
        int receivedCount,
        int consecutiveFailures,
        Instant startedAt,
        Duration duration,
        String errorMessage) {

    public static PollingStatusResponse from(PollingCycleResult result) {
        return new PollingStatusResponse(
                result.outcome(),
                result.skipReason(),
                result.windowStart(),
                result.windowEnd(),
                result.receivedCount(),
                result.consecutiveFailures(),
                result.startedAt(),
                result.duration(),
                result.errorMessage());
    }
}

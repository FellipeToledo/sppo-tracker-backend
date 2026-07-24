package com.fajtech.sppotracker.infrastructure.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuração da máquina de desvio (docs/regras-de-negocio.md §5, §8). Vinculada a
 * {@code gps.route.deviation}. Só tem efeito quando a fonte de shapes está ligada
 * ({@code gps.route.shape-source=gtfs-service}), pois a detecção depende de geometria.
 *
 * @param enabled                  liga/desliga a detecção de desvio (default true quando há shapes)
 * @param confirmationMarginMeters margem de "efetivamente fora" (§5.2, default 30 m)
 * @param alertPoints              pontos consecutivos fora p/ ALERT (default 3)
 * @param confirmSustained         tempo sustentado p/ CONFIRMED (default 3 min)
 * @param confirmDistanceMeters    distância p/ CONFIRMED imediato (default 150 m)
 * @param returnPoints             pontos consecutivos dentro p/ RETURN/CANCELLED (default 3)
 * @param severityMedioMeters      limite superior de LEVE (§5.4, default 150 m)
 * @param severityGraveMeters      limite superior de MEDIO (§5.4, default 500 m)
 * @param stateTtl                 validade do estado por veículo sem observação (default 6 h)
 * @param sweepInterval            intervalo do sweep de consolidação (default 30 s)
 */
@ConfigurationProperties(prefix = "gps.route.deviation")
public record RouteDeviationProperties(
        boolean enabled,
        double confirmationMarginMeters,
        int alertPoints,
        Duration confirmSustained,
        double confirmDistanceMeters,
        int returnPoints,
        double severityMedioMeters,
        double severityGraveMeters,
        Duration stateTtl,
        Duration sweepInterval) {

    public RouteDeviationProperties {
        if (confirmationMarginMeters <= 0) {
            confirmationMarginMeters = 30.0;
        }
        if (alertPoints < 1) {
            alertPoints = 3;
        }
        if (confirmSustained == null || confirmSustained.isZero() || confirmSustained.isNegative()) {
            confirmSustained = Duration.ofMinutes(3);
        }
        if (confirmDistanceMeters <= 0) {
            confirmDistanceMeters = 150.0;
        }
        if (returnPoints < 1) {
            returnPoints = 3;
        }
        if (severityMedioMeters <= 0) {
            severityMedioMeters = 150.0;
        }
        if (severityGraveMeters <= 0) {
            severityGraveMeters = 500.0;
        }
        if (stateTtl == null || stateTtl.isZero() || stateTtl.isNegative()) {
            stateTtl = Duration.ofHours(6);
        }
        if (sweepInterval == null || sweepInterval.isZero() || sweepInterval.isNegative()) {
            sweepInterval = Duration.ofSeconds(30);
        }
    }
}

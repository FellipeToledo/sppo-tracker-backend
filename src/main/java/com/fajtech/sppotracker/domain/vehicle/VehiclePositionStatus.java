package com.fajtech.sppotracker.domain.vehicle;

/**
 * Status final da classificação de uma posição. A precedência entre eles é
 * decidida no pipeline de classificação (ver docs/regras-de-negocio.md, §4).
 */
public enum VehiclePositionStatus {
    IN_OPERATION,
    IN_GARAGE,
    OUT_OF_MUNICIPALITY,
    OUT_OF_ROUTE,
    SUSPICIOUS,
    INVALID,
    STALE
}

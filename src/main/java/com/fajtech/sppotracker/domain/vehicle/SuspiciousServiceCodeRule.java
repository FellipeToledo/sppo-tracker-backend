package com.fajtech.sppotracker.domain.vehicle;

import java.time.Instant;
import java.util.Set;

/**
 * Código de serviço suspeito: {@code serviceCode} normalizado pertence ao conjunto
 * suspeito (docs/regras-de-negocio.md §4.3).
 */
public class SuspiciousServiceCodeRule implements ClassificationRule {

    private static final Set<String> SUSPICIOUS_CODES = Set.of(
            "MANUTENCAO", "MANUT", "VISTORIA",
            "FORA DE OP", "FORA OP", "FORA DE OPERACAO",
            "TREINO", "TREINA", "OPER.", "00000");

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        String normalized = ServiceCodeNormalizer.normalize(position.serviceCode());
        return SUSPICIOUS_CODES.contains(normalized)
                ? Set.of(ClassificationTag.SUSPICIOUS_SERVICE_CODE)
                : Set.of();
    }
}

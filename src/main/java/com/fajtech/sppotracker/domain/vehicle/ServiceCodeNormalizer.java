package com.fajtech.sppotracker.domain.vehicle;

import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * Normaliza o {@code serviceCode} para comparação nas regras de código
 * (docs/regras-de-negocio.md §4.3): remove acentos, {@code trim}, colapsa espaços
 * internos e converte para maiúsculas. Entrada nula/vazia vira string vazia.
 */
public final class ServiceCodeNormalizer {

    private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private ServiceCodeNormalizer() {
    }

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String withoutAccents = COMBINING_MARKS
                .matcher(Normalizer.normalize(raw, Normalizer.Form.NFD))
                .replaceAll("");
        return WHITESPACE.matcher(withoutAccents.trim()).replaceAll(" ").toUpperCase();
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto;

import com.fajtech.sppotracker.domain.operator.Operator;

/** Representação REST de uma operadora (docs/regras-de-negocio.md §7.1). */
public record OperatorResponse(String prefix, String name) {

    public static OperatorResponse from(Operator operator) {
        return new OperatorResponse(operator.prefix(), operator.name());
    }
}

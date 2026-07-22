package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.OperatorResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint do de-para de operadoras (docs/regras-de-negocio.md §7.1).
 */
@RestController
@RequestMapping("/api/v1/operators")
public class OperatorController {

    private final OperatorQueryUseCase useCase;

    public OperatorController(OperatorQueryUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<OperatorResponse> operators() {
        return useCase.getAll().stream().map(OperatorResponse::from).toList();
    }
}

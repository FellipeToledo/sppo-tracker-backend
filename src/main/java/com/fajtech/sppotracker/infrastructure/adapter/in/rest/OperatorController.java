package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import com.fajtech.sppotracker.application.port.in.OperatorQueryUseCase;
import com.fajtech.sppotracker.domain.operator.Consortium;
import com.fajtech.sppotracker.infrastructure.adapter.in.rest.dto.CompanyResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Endpoint do de-para de operadoras (docs/regras-de-negocio.md §7.1). Lista as
 * empresas conhecidas, cada uma anotada com o consórcio a que pertence.
 */
@RestController
@RequestMapping("/api/v1/operators")
public class OperatorController {

    private final OperatorQueryUseCase useCase;

    public OperatorController(OperatorQueryUseCase useCase) {
        this.useCase = useCase;
    }

    @GetMapping
    public List<CompanyResponse> operators() {
        Map<String, Consortium> byCode = useCase.getConsortiums().stream()
                .collect(Collectors.toMap(Consortium::code, Function.identity()));
        return useCase.getCompanies().stream()
                .map(company -> CompanyResponse.from(company, byCode.get(company.consortiumCode())))
                .toList();
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.in.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Fallback de erros REST em {@code application/problem+json} (docs/regras-de-negocio.md §9).
 * As exceções padrão do Spring MVC (400/404/405/…) já viram problem+json via
 * {@code spring.mvc.problemdetails.enabled=true}; este handler cobre apenas falhas
 * inesperadas, sem vazar stack trace nem mensagem interna.
 */
@RestControllerAdvice
public class RestExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(RestExceptionHandler.class);

    /** Falha inesperada — não vaza detalhes internos. */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        log.error("Unhandled exception in REST layer", ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred");
        problem.setTitle("Internal server error");
        return problem;
    }
}

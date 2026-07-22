package com.fajtech.sppotracker.infrastructure.adapter.out.operator;

import com.fajtech.sppotracker.application.port.out.OperatorDirectoryPort;
import com.fajtech.sppotracker.domain.operator.Operator;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * De-para de consórcios carregado de um JSON empacotado (docs/regras-de-negocio.md
 * §6), uma vez na inicialização — fora do hot path. Resolve pelo <b>primeiro
 * caractere da ordem</b> ({@code vehicleId}, upper-case), que identifica o
 * consórcio (A–D; ver §1: formato {@code XYYZZZ}).
 */
@Component
public class PackagedOperatorDirectory implements OperatorDirectoryPort {

    private static final int PREFIX_LENGTH = 1;

    private final Resource resource;
    private final ObjectMapper objectMapper;
    private final Map<String, Operator> byPrefix = new LinkedHashMap<>();

    public PackagedOperatorDirectory(@Value("classpath:operators.json") Resource resource,
                                     ObjectMapper objectMapper) {
        this.resource = resource;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        try (InputStream in = resource.getInputStream()) {
            List<Operator> operators = objectMapper.readValue(in, new OperatorListType());
            byPrefix.clear();
            for (Operator operator : operators) {
                byPrefix.put(operator.prefix().toUpperCase(Locale.ROOT), operator);
            }
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Falha ao carregar o de-para de operadoras", e);
        }
    }

    @Override
    public Optional<Operator> findByVehicleId(String vehicleId) {
        if (vehicleId == null || vehicleId.length() < PREFIX_LENGTH) {
            return Optional.empty();
        }
        String key = vehicleId.substring(0, PREFIX_LENGTH).toUpperCase(Locale.ROOT);
        return Optional.ofNullable(byPrefix.get(key));
    }

    @Override
    public List<Operator> findAll() {
        return List.copyOf(byPrefix.values());
    }

    /** Tipo auxiliar para desserializar a lista de operadoras. */
    private static final class OperatorListType
            extends tools.jackson.core.type.TypeReference<List<Operator>> {
    }
}

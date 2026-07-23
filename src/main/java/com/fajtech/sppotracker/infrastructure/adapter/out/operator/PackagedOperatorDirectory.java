package com.fajtech.sppotracker.infrastructure.adapter.out.operator;

import com.fajtech.sppotracker.application.port.out.OperatorDirectoryPort;
import com.fajtech.sppotracker.domain.operator.Company;
import com.fajtech.sppotracker.domain.operator.Consortium;
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
 * De-para de operadoras carregado de JSONs empacotados (docs/regras-de-negocio.md
 * §6), uma vez na inicialização — fora do hot path. Resolve dois níveis a partir da
 * ordem ({@code vehicleId}, ver §1: formato {@code XYYZZZ}):
 *
 * <ul>
 *   <li><b>Consórcio</b> pelo <b>primeiro caractere</b> (A–D) — cobre toda a frota;</li>
 *   <li><b>Empresa</b> pelos <b>quatro primeiros caracteres</b> (letra + 3 dígitos)
 *       — cobertura parcial (só os prefixos presentes no de-para).</li>
 * </ul>
 *
 * <p>Ambos os arquivos são objetos JSON {@code chave -> nome}.
 */
@Component
public class PackagedOperatorDirectory implements OperatorDirectoryPort {

    private static final int CONSORTIUM_KEY_LENGTH = 1;
    private static final int COMPANY_KEY_LENGTH = 4;

    private final Resource consortiumsResource;
    private final Resource companiesResource;
    private final ObjectMapper objectMapper;
    private final Map<String, Consortium> consortiumsByCode = new LinkedHashMap<>();
    private final Map<String, Company> companiesByPrefix = new LinkedHashMap<>();

    public PackagedOperatorDirectory(@Value("classpath:consortiums.json") Resource consortiumsResource,
                                     @Value("classpath:companies.json") Resource companiesResource,
                                     ObjectMapper objectMapper) {
        this.consortiumsResource = consortiumsResource;
        this.companiesResource = companiesResource;
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void load() {
        consortiumsByCode.clear();
        readMapping(consortiumsResource).forEach((code, name) ->
                consortiumsByCode.put(code.toUpperCase(Locale.ROOT), new Consortium(code, name)));
        companiesByPrefix.clear();
        readMapping(companiesResource).forEach((prefix, name) ->
                companiesByPrefix.put(prefix.toUpperCase(Locale.ROOT), new Company(prefix, name)));
    }

    private Map<String, String> readMapping(Resource resource) {
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readValue(in, new MappingType());
        } catch (IOException | JacksonException e) {
            throw new IllegalStateException("Falha ao carregar o de-para de operadoras de " + resource.getFilename(), e);
        }
    }

    @Override
    public Optional<Consortium> findConsortium(String vehicleId) {
        return lookup(consortiumsByCode, vehicleId, CONSORTIUM_KEY_LENGTH);
    }

    @Override
    public Optional<Company> findCompany(String vehicleId) {
        return lookup(companiesByPrefix, vehicleId, COMPANY_KEY_LENGTH);
    }

    private static <T> Optional<T> lookup(Map<String, T> byKey, String vehicleId, int keyLength) {
        if (vehicleId == null || vehicleId.length() < keyLength) {
            return Optional.empty();
        }
        String key = vehicleId.substring(0, keyLength).toUpperCase(Locale.ROOT);
        return Optional.ofNullable(byKey.get(key));
    }

    @Override
    public List<Consortium> allConsortiums() {
        return List.copyOf(consortiumsByCode.values());
    }

    @Override
    public List<Company> allCompanies() {
        return List.copyOf(companiesByPrefix.values());
    }

    /** Tipo auxiliar para desserializar o objeto JSON {@code chave -> nome}. */
    private static final class MappingType
            extends tools.jackson.core.type.TypeReference<LinkedHashMap<String, String>> {
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.out.operator;

import com.fajtech.sppotracker.domain.operator.Company;
import com.fajtech.sppotracker.domain.operator.Consortium;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Carregamento do de-para de operadoras do classpath (docs/regras-de-negocio.md §6). */
class PackagedOperatorDirectoryTest {

    private PackagedOperatorDirectory directory;

    @BeforeEach
    void setUp() {
        directory = new PackagedOperatorDirectory(
                new ClassPathResource("consortiums.json"),
                new ClassPathResource("companies.json"),
                JsonMapper.builder().build());
        directory.load();
    }

    @Test
    void shouldResolveConsortiumByFirstCharCaseInsensitive() {
        assertThat(directory.findConsortium("a26123"))
                .get()
                .extracting(Consortium::code, Consortium::name)
                .containsExactly("A", "Consórcio Intersul");
    }

    @Test
    void shouldResolveCompanyByFourCharPrefixCaseInsensitive() {
        assertThat(directory.findCompany("a410999"))
                .get()
                .extracting(Company::prefix, Company::name)
                .containsExactly("A410", "Real Auto Onibus Ltda");
    }

    @Test
    void shouldReturnEmptyCompanyWhenPrefixNotMapped() {
        // Consórcio conhecido (A), mas sem empresa para o prefixo "A261".
        assertThat(directory.findConsortium("A26123")).isPresent();
        assertThat(directory.findCompany("A26123")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownConsortium() {
        assertThat(directory.findConsortium("Z99999")).isEmpty();
        assertThat(directory.findCompany("Z99999")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForShortOrNullVehicleId() {
        assertThat(directory.findConsortium("")).isEmpty();
        assertThat(directory.findConsortium(null)).isEmpty();
        assertThat(directory.findCompany("A26")).isEmpty();
        assertThat(directory.findCompany(null)).isEmpty();
    }

    @Test
    void shouldExposeAllConsortia() {
        assertThat(directory.allConsortiums())
                .extracting(Consortium::code, Consortium::name)
                .containsExactly(
                        tuple("A", "Consórcio Intersul"),
                        tuple("B", "Consórcio Internorte"),
                        tuple("C", "Consórcio Transcarioca"),
                        tuple("D", "Consórcio Santa Cruz"));
    }

    @Test
    void shouldExposeAllCompaniesWithConsistentPrefixes() {
        assertThat(directory.allCompanies())
                .isNotEmpty()
                .allSatisfy(company -> {
                    assertThat(company.prefix()).hasSize(4);
                    assertThat(company.name()).isNotBlank();
                });
    }
}

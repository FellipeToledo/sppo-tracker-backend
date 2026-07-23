package com.fajtech.sppotracker.application.operator;

import com.fajtech.sppotracker.application.port.out.OperatorDirectoryPort;
import com.fajtech.sppotracker.domain.operator.Company;
import com.fajtech.sppotracker.domain.operator.Consortium;
import com.fajtech.sppotracker.domain.operator.VehicleOperator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Casos de uso de operadora delegam ao de-para (docs/regras-de-negocio.md §6). */
class OperatorServiceTest {

    private OperatorDirectoryPort directory;
    private OperatorService service;

    @BeforeEach
    void setUp() {
        directory = mock(OperatorDirectoryPort.class);
        service = new OperatorService(directory);
    }

    @Test
    void shouldReturnAllConsortiums() {
        List<Consortium> all = List.of(new Consortium("A", "Consórcio Intersul"));
        when(directory.allConsortiums()).thenReturn(all);

        assertThat(service.getConsortiums()).isEqualTo(all);
    }

    @Test
    void shouldReturnAllCompanies() {
        List<Company> all = List.of(new Company("A410", "Real Auto Onibus Ltda"));
        when(directory.allCompanies()).thenReturn(all);

        assertThat(service.getCompanies()).isEqualTo(all);
    }

    @Test
    void shouldResolveBothConsortiumAndCompany() {
        Consortium consortium = new Consortium("A", "Consórcio Intersul");
        Company company = new Company("A410", "Real Auto Onibus Ltda");
        when(directory.findConsortium("A410999")).thenReturn(Optional.of(consortium));
        when(directory.findCompany("A410999")).thenReturn(Optional.of(company));

        VehicleOperator operator = service.resolve("A410999");

        assertThat(operator.consortium()).isEqualTo(consortium);
        assertThat(operator.company()).isEqualTo(company);
        assertThat(operator.consortiumName()).isEqualTo("Consórcio Intersul");
        assertThat(operator.companyName()).isEqualTo("Real Auto Onibus Ltda");
    }

    @Test
    void shouldResolveConsortiumOnlyWhenCompanyUnknown() {
        when(directory.findConsortium("A26123")).thenReturn(Optional.of(new Consortium("A", "Consórcio Intersul")));
        when(directory.findCompany("A26123")).thenReturn(Optional.empty());

        VehicleOperator operator = service.resolve("A26123");

        assertThat(operator.consortiumName()).isEqualTo("Consórcio Intersul");
        assertThat(operator.company()).isNull();
        assertThat(operator.companyName()).isNull();
    }

    @Test
    void shouldResolveNothingForUnknownVehicle() {
        when(directory.findConsortium("Z99999")).thenReturn(Optional.empty());
        when(directory.findCompany("Z99999")).thenReturn(Optional.empty());

        VehicleOperator operator = service.resolve("Z99999");

        assertThat(operator.consortium()).isNull();
        assertThat(operator.company()).isNull();
        assertThat(operator.consortiumName()).isNull();
        assertThat(operator.companyName()).isNull();
    }
}

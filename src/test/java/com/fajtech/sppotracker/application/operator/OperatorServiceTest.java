package com.fajtech.sppotracker.application.operator;

import com.fajtech.sppotracker.application.port.out.OperatorDirectoryPort;
import com.fajtech.sppotracker.domain.operator.Operator;
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
    void shouldReturnAllOperators() {
        List<Operator> all = List.of(new Operator("A26I", "Empresa X"), new Operator("B28R", "Empresa Y"));
        when(directory.findAll()).thenReturn(all);

        assertThat(service.getAll()).isEqualTo(all);
    }

    @Test
    void shouldResolveOperatorByVehicleId() {
        Operator op = new Operator("A26I", "Empresa X");
        when(directory.findByVehicleId("A26I001")).thenReturn(Optional.of(op));

        assertThat(service.resolve("A26I001")).contains(op);
    }

    @Test
    void shouldReturnEmptyForUnknownVehicle() {
        when(directory.findByVehicleId("ZZZZ999")).thenReturn(Optional.empty());

        assertThat(service.resolve("ZZZZ999")).isEmpty();
    }
}

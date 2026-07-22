package com.fajtech.sppotracker.infrastructure.adapter.out.operator;

import com.fajtech.sppotracker.domain.operator.Operator;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Carregamento do de-para de operadoras do classpath (docs/regras-de-negocio.md §6). */
class PackagedOperatorDirectoryTest {

    private PackagedOperatorDirectory directory;

    @BeforeEach
    void setUp() throws Exception {
        directory = new PackagedOperatorDirectory(new ClassPathResource("operators.json"), JsonMapper.builder().build());
        directory.load();
    }

    @Test
    void shouldResolveByFourCharPrefixCaseInsensitive() {
        Optional<Operator> op = directory.findByVehicleId("a26i001");
        assertThat(op).isPresent();
        assertThat(op.get().prefix()).isEqualTo("A26I");
    }

    @Test
    void shouldReturnEmptyForUnknownPrefix() {
        assertThat(directory.findByVehicleId("ZZZZ999")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForShortOrNullVehicleId() {
        assertThat(directory.findByVehicleId("A26")).isEmpty();
        assertThat(directory.findByVehicleId(null)).isEmpty();
    }

    @Test
    void shouldExposeAllOperators() {
        assertThat(directory.findAll())
                .extracting(Operator::prefix)
                .contains("A26I", "B28R", "C41O", "D53T");
    }
}

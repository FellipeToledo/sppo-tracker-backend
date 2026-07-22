package com.fajtech.sppotracker.infrastructure.adapter.out.operator;

import com.fajtech.sppotracker.domain.operator.Operator;
import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Carregamento do de-para de consórcios do classpath (docs/regras-de-negocio.md §6). */
class PackagedOperatorDirectoryTest {

    private PackagedOperatorDirectory directory;

    @BeforeEach
    void setUp() throws Exception {
        directory = new PackagedOperatorDirectory(new ClassPathResource("operators.json"), JsonMapper.builder().build());
        directory.load();
    }

    @Test
    void shouldResolveConsortiumByFirstCharCaseInsensitive() {
        Optional<Operator> op = directory.findByVehicleId("a26123");
        assertThat(op).isPresent();
        assertThat(op.get().prefix()).isEqualTo("A");
        assertThat(op.get().name()).isEqualTo("Consórcio Intersul");
    }

    @Test
    void shouldReturnEmptyForUnknownFirstChar() {
        assertThat(directory.findByVehicleId("Z99999")).isEmpty();
    }

    @Test
    void shouldReturnEmptyForEmptyOrNullVehicleId() {
        assertThat(directory.findByVehicleId("")).isEmpty();
        assertThat(directory.findByVehicleId(null)).isEmpty();
    }

    @Test
    void shouldExposeAllConsortia() {
        assertThat(directory.findAll())
                .extracting(Operator::prefix)
                .containsExactly("A", "B", "C", "D");
    }
}

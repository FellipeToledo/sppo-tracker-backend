package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import com.fajtech.sppotracker.domain.vehicle.PositionSource;
import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Traduz o item cru do feed da SMTR ({@link DadosMobilidadeRioGpsItem}) para o
 * domínio ({@link VehiclePosition}), aplicando parsing defensivo.
 *
 * <p>Registro malformado (sem {@code ordem}, sem coordenadas válidas ou sem os
 * timestamps obrigatórios) é <b>descartado individualmente</b> — nunca derruba o
 * lote (docs/regras-de-negocio.md §1, §9). O feed público não fornece
 * {@code directionCode}/{@code routeId}/{@code tripId}/{@code shapeId}/heading,
 * que ficam nulos.
 */
@Component
public class DadosMobilidadeRioGpsMapper {

    private static final Logger log = LoggerFactory.getLogger(DadosMobilidadeRioGpsMapper.class);

    private final Clock clock;

    public DadosMobilidadeRioGpsMapper(Clock clock) {
        this.clock = clock;
    }

    /** Mapeia um lote, descartando itens malformados individualmente. */
    public List<VehiclePosition> mapAll(List<DadosMobilidadeRioGpsItem> items) {
        if (items == null || items.isEmpty()) {
            return List.of();
        }
        return items.stream()
                .map(this::map)
                .flatMap(Optional::stream)
                .toList();
    }

    /** Mapeia um item; {@link Optional#empty()} se malformado. */
    public Optional<VehiclePosition> map(DadosMobilidadeRioGpsItem item) {
        if (item == null) {
            return Optional.empty();
        }
        try {
            Coordinates coordinates = coordinates(item.latitude(), item.longitude());
            Instant positionTimestamp = EpochParser.parse(item.datahora());
            Instant sentTimestamp = EpochParser.parse(item.datahoraenvio());
            if (coordinates == null || positionTimestamp == null || sentTimestamp == null) {
                return discard(item);
            }
            VehiclePosition position = VehiclePosition.builder()
                    .vehicleId(item.ordem())
                    .serviceCode(trimToNull(item.linha()))
                    .coordinates(coordinates)
                    .speed(speed(item.velocidade()))
                    .positionTimestamp(positionTimestamp)
                    .sentTimestamp(sentTimestamp)
                    .serverTimestamp(EpochParser.parse(item.datahoraservidor()))
                    .receivedAt(clock.instant())
                    .source(PositionSource.DADOS_MOBILIDADE_RIO)
                    .build();
            return Optional.of(position);
        } catch (RuntimeException e) {
            // vehicleId em branco, coordenadas fora de faixa, etc.
            return discard(item);
        }
    }

    private static Coordinates coordinates(String rawLat, String rawLon) {
        BigDecimal latitude = DecimalParser.parse(rawLat);
        BigDecimal longitude = DecimalParser.parse(rawLon);
        if (latitude == null || longitude == null) {
            return null;
        }
        return new Coordinates(latitude, longitude);
    }

    private static Double speed(String rawSpeed) {
        BigDecimal parsed = DecimalParser.parse(rawSpeed);
        return parsed == null ? null : parsed.doubleValue();
    }

    private static Optional<VehiclePosition> discard(DadosMobilidadeRioGpsItem item) {
        log.debug("Descartando registro GPS malformado: ordem={}", item.ordem());
        return Optional.empty();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

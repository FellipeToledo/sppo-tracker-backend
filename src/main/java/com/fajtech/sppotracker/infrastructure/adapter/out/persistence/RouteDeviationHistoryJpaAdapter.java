package com.fajtech.sppotracker.infrastructure.adapter.out.persistence;

import com.fajtech.sppotracker.application.port.out.RouteDeviationHistoryPort;
import com.fajtech.sppotracker.application.route.RouteDeviationHistoryEntry;
import com.fajtech.sppotracker.application.route.RouteDeviationQuery;
import com.fajtech.sppotracker.domain.route.DeviationEventType;
import com.fajtech.sppotracker.domain.route.DeviationSeverity;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Adapter de leitura do histórico de desvios (docs/regras-de-negocio.md §7.4):
 * implementa {@link RouteDeviationHistoryPort} sobre o repositório JPA e mapeia a
 * entidade para o registro de leitura da aplicação.
 */
@Component
public class RouteDeviationHistoryJpaAdapter implements RouteDeviationHistoryPort {

    private final RouteDeviationEventRepository repository;

    public RouteDeviationHistoryJpaAdapter(RouteDeviationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<RouteDeviationHistoryEntry> findRecent(RouteDeviationQuery query) {
        return repository.search(
                        query.vehicleId(),
                        query.serviceCode(),
                        query.type() == null ? null : query.type().name(),
                        query.severity() == null ? null : query.severity().name(),
                        PageRequest.of(0, query.limit()))
                .stream()
                .map(RouteDeviationHistoryJpaAdapter::toEntry)
                .toList();
    }

    private static RouteDeviationHistoryEntry toEntry(RouteDeviationEventEntity entity) {
        return new RouteDeviationHistoryEntry(
                entity.getId(),
                entity.getVehicleId(),
                entity.getServiceCode(),
                entity.getRouteId(),
                parseType(entity.getEventType()),
                parseSeverity(entity.getSeverity()),
                entity.getDistanceMeters(),
                entity.getOccurredAt());
    }

    private static DeviationEventType parseType(String value) {
        try {
            return value == null ? null : DeviationEventType.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }

    private static DeviationSeverity parseSeverity(String value) {
        try {
            return value == null ? null : DeviationSeverity.valueOf(value);
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.out.persistence;

import com.fajtech.sppotracker.application.port.out.RecordRouteDeviationEventPort;
import com.fajtech.sppotracker.domain.route.RouteDeviationEvent;

/**
 * Escritor JPA do histórico de eventos de desvio (docs/regras-de-negocio.md §5.5, §7.4).
 * Implementa {@link RecordRouteDeviationEventPort} mapeando o evento de domínio para a
 * entidade {@link RouteDeviationEventEntity}. {@code severity} do enum → nome; a linha
 * (serviceCode) e a rota (routeId, quando houver) são gravadas.
 *
 * <p>Registrado como bean condicional (só com a detecção ativa), para não exigir JPA
 * quando a fonte de shapes está desligada.
 */
public class JpaRouteDeviationEventWriter implements RecordRouteDeviationEventPort {

    private final RouteDeviationEventRepository repository;

    public JpaRouteDeviationEventWriter(RouteDeviationEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(RouteDeviationEvent event) {
        RouteDeviationEventEntity entity = new RouteDeviationEventEntity();
        entity.setVehicleId(event.vehicleId());
        entity.setServiceCode(event.serviceCode());
        entity.setRouteId(event.routeId());
        entity.setEventType(event.type().name());
        entity.setSeverity(event.severity() == null ? null : event.severity().name());
        entity.setDistanceMeters(event.distanceMeters());
        entity.setOccurredAt(event.occurredAt());
        repository.save(entity);
    }
}

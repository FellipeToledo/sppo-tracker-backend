package com.fajtech.sppotracker.domain.vehicle;

import com.fajtech.sppotracker.domain.geofence.GarageLocator;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Garagem por geofence (docs/regras-de-negocio.md §4.3): a posição cai dentro de
 * algum polígono de garagem ⇒ tag {@code GARAGE_GEOFENCE}, que o
 * {@link PositionClassifier} resolve como status {@code IN_GARAGE} (§4.2).
 *
 * <p>É a fonte <b>principal</b> de garagem, porque olha onde o veículo está de fato:
 * pega o ônibus recolhido que continua transmitindo um código de serviço operacional
 * — caso que a {@link GarageServiceCodeRule} não vê. As duas coexistem; a regra por
 * código de serviço segue como fallback.
 *
 * <p>Sem polígonos carregados a regra é <b>neutra</b> (mesmo contrato defensivo da
 * {@link OutOfRouteRule}): nunca derruba a classificação por falta de dado.
 * Coordenada (0,0) — lixo do feed — nunca produz a tag.
 *
 * <p>A decisão de pertinência fica toda no {@link GarageLocator}, que a borda
 * REST/WebSocket também usa para expor o <i>nome</i> da garagem — uma única fonte,
 * para tag e nome nunca discordarem.
 */
public final class GarageGeofenceRule implements ClassificationRule {

    private final GarageLocator locator;

    public GarageGeofenceRule(GarageLocator locator) {
        this.locator = Objects.requireNonNull(locator, "locator");
    }

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        return locator.locate(position.coordinates()).isPresent()
                ? Set.of(ClassificationTag.GARAGE_GEOFENCE)
                : Set.of();
    }
}

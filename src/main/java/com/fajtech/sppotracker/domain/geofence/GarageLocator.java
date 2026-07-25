package com.fajtech.sppotracker.domain.geofence;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Responde <b>em qual</b> garagem uma coordenada cai (docs/regras-de-negocio.md §4.3).
 *
 * <p>É a <b>lógica única</b> de pertinência, compartilhada pela
 * {@code GarageGeofenceRule} (que só precisa do sim/não) e pela borda REST/WebSocket
 * (que expõe o nome da garagem) — mesmo motivo pelo qual a aderência de rota tem um
 * único {@code RouteAdherenceEvaluator}: se as duas respostas divergirem, o veículo
 * aparece "em garagem" sem garagem, ou o contrário.
 *
 * <p>Value object puro. A varredura é linear nas áreas, cada uma com pré-filtro de
 * bounding box — barato para as poucas dezenas de garagens do SPPO. Se o dataset
 * crescer uma ordem de grandeza, indexar por célula antes de otimizar aqui.
 */
public final class GarageLocator {

    private final List<GarageArea> garages;

    public GarageLocator(List<GarageArea> garages) {
        this.garages = List.copyOf(Objects.requireNonNull(garages, "garages"));
    }

    /** Áreas carregadas, na ordem do dataset. */
    public List<GarageArea> garages() {
        return garages;
    }

    public boolean isEmpty() {
        return garages.isEmpty();
    }

    /**
     * A primeira área que contém a coordenada, se houver. Coordenada nula ou (0,0)
     * — lixo do feed (§1) — nunca localiza garagem.
     */
    public Optional<GarageArea> locate(Coordinates position) {
        if (garages.isEmpty() || position == null || position.isZeroZero()) {
            return Optional.empty();
        }
        for (GarageArea garage : garages) {
            if (garage.contains(position)) {
                return Optional.of(garage);
            }
        }
        return Optional.empty();
    }
}

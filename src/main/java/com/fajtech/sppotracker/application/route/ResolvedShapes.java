package com.fajtech.sppotracker.application.route;

import com.fajtech.sppotracker.domain.route.RoutePath;

import java.util.List;

/**
 * Resultado autoritativo da resolução de itinerário de uma linha pela fonte externa
 * (docs/regras-de-negocio.md §4.4). {@code paths} vazio = a linha existe mas não tem
 * geometria ({@code no_shapes}) ou não foi encontrada; ainda assim é um resultado
 * definitivo e pode ser cacheado. {@code feedVersionId} identifica a versão do feed
 * GTFS vigente (usado para telemetria e futura invalidação por versão).
 */
public record ResolvedShapes(String feedVersionId, List<RoutePath> paths) {

    public ResolvedShapes {
        paths = paths == null ? List.of() : List.copyOf(paths);
    }

    public static ResolvedShapes empty(String feedVersionId) {
        return new ResolvedShapes(feedVersionId, List.of());
    }
}

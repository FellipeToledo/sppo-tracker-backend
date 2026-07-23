package com.fajtech.sppotracker.domain.route;

import java.util.List;

/**
 * Fonte de geometria de itinerário por linha (docs/regras-de-negocio.md §4.4),
 * consultada pela {@code OutOfRouteRule} durante a classificação.
 *
 * <p><b>Contrato:</b> a implementação é <b>não-bloqueante</b> e roda no hot path —
 * devolve apenas a geometria já resolvida em cache. Uma lista vazia significa
 * "sem itinerário conhecido" (ainda não resolvido, linha inexistente ou sem shapes)
 * e nunca deve derrubar a classificação. A resolução real (I/O) acontece em
 * background, fora deste método.
 */
public interface RouteGeometrySource {

    List<RoutePath> pathsFor(String serviceCode);
}

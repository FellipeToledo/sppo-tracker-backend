package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.application.route.ResolvedShapes;

import java.util.Optional;

/**
 * Porta de saída para resolver a geometria de itinerário (shapes) de uma linha a
 * partir de uma fonte externa — o {@code sppo-gtfs-service} (docs/regras-de-negocio.md
 * §4.4, §10). Operação <b>bloqueante</b> (I/O): é chamada apenas em background pelo
 * cache, nunca no hot path.
 *
 * <p>Semântica do retorno:
 * <ul>
 *   <li>{@code Optional.of(...)} — resultado <b>autoritativo</b> (inclusive vazio para
 *       linha sem shapes / inexistente); pode ser cacheado;</li>
 *   <li>{@code Optional.empty()} — falha transitória (timeout, indisponível): não
 *       cachear, re-tentar depois.</li>
 * </ul>
 */
public interface ResolveRouteShapesPort {

    Optional<ResolvedShapes> resolve(String serviceCode);
}

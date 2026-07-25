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

    /**
     * <b>Por que</b> {@link #pathsFor} veio vazio — a única forma de separar "a linha não
     * tem itinerário no feed" (persistente) de "ainda não resolvi" (transitório). Sem essa
     * distinção, o número de veículos sem itinerário infla a cada reinício do serviço.
     *
     * <p>Não dispara resolução: é leitura pura, para o hot path.
     *
     * <p>O default é <b>conservador</b>: sempre {@code PENDING}. Quem não sabe distinguir
     * nunca afirma que a linha está fora do planejamento. Não consulta {@link #pathsFor}
     * — isso agendaria resolução (o cache o faz) e faria uma segunda busca no hot path,
     * quebrando o próprio contrato deste método. O chamador só pergunta quando
     * {@code pathsFor} já veio vazio, então {@code RESOLVED} não é resposta útil aqui.
     */
    default Availability availabilityFor(String serviceCode) {
        return Availability.PENDING;
    }

    enum Availability {
        /** Geometria em cache. */
        RESOLVED,
        /** A fonte respondeu que a linha não tem itinerário no feed vigente. */
        NOT_PLANNED,
        /** Nunca resolvida (cache frio, resolução em background, executor saturado). */
        PENDING
    }
}

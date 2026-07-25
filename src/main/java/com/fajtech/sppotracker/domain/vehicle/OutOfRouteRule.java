package com.fajtech.sppotracker.domain.vehicle;

import com.fajtech.sppotracker.domain.route.LineCodeKey;
import com.fajtech.sppotracker.domain.route.RouteAdherence;
import com.fajtech.sppotracker.domain.route.RouteAdherenceEvaluator;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;

/**
 * Fora de rota (docs/regras-de-negocio.md §4.4): a posição está fora do corredor de
 * <b>todos</b> os shapes resolvidos da linha. Delega a decisão dentro/fora ao
 * {@link RouteAdherenceEvaluator} (lógica única, compartilhada com a máquina de desvio §5).
 *
 * <ul>
 *   <li>{@code OUTSIDE} ⇒ tag {@code OUT_OF_ROUTE};</li>
 *   <li>{@code INSIDE} ⇒ sem tag (aderente);</li>
 *   <li>{@code UNRESOLVED} com código <b>de linha</b> (linha sem planejamento no feed, ou
 *       geometria ainda não resolvida) ⇒ tag {@code ROUTE_UNRESOLVED}: a posição
 *       <b>não é descartada nem promovida</b> a "na rota" — fica rotulada como não
 *       avaliada, e o status continua sendo decidido pelas outras regras;</li>
 *   <li>{@code UNRESOLVED} com código que não é linha ({@code GARAGEM}, {@code ESPECIAL})
 *       ⇒ sem tag: não há itinerário a avaliar.</li>
 * </ul>
 *
 * <p>Em nenhum caso a regra derruba a classificação — ela só acrescenta rótulo.
 */
public final class OutOfRouteRule implements ClassificationRule {

    private final RouteAdherenceEvaluator evaluator;

    public OutOfRouteRule(RouteAdherenceEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        String serviceCode = position.serviceCode();
        RouteAdherence adherence = evaluator.evaluate(serviceCode, position.coordinates());
        if (adherence.isOutside()) {
            return Set.of(ClassificationTag.OUT_OF_ROUTE);
        }
        // Nenhuma posição é descartada: quando não houve o que avaliar, rotula-se
        // ROUTE_UNRESOLVED em vez de deixar passar como se estivesse na rota. Só para
        // código que É de linha — GARAGEM/ESPECIAL não têm itinerário a avaliar.
        if (adherence.isUnresolved()
                && LineCodeKey.looksLikeLine(serviceCode)
                && !position.coordinates().isZeroZero()) {
            return Set.of(ClassificationTag.ROUTE_UNRESOLVED);
        }
        return Set.of();
    }
}

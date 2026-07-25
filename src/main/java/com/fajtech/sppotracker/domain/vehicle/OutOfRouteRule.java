package com.fajtech.sppotracker.domain.vehicle;

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
 *   <li>{@code INSIDE} ou {@code UNRESOLVED} (sem geometria / linha inexistente / sem
 *       shapes) ⇒ sem tag: a regra fica neutra e nunca derruba a classificação.</li>
 * </ul>
 */
public final class OutOfRouteRule implements ClassificationRule {

    private final RouteAdherenceEvaluator evaluator;

    public OutOfRouteRule(RouteAdherenceEvaluator evaluator) {
        this.evaluator = Objects.requireNonNull(evaluator, "evaluator");
    }

    @Override
    public Set<ClassificationTag> evaluate(VehiclePosition position, Instant now) {
        RouteAdherence adherence = evaluator.evaluate(position.serviceCode(), position.coordinates());
        return adherence.isOutside() ? Set.of(ClassificationTag.OUT_OF_ROUTE) : Set.of();
    }
}

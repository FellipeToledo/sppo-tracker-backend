package com.fajtech.sppotracker.domain.route;

/**
 * Resultado da avaliação de aderência ao itinerário (docs/regras-de-negocio.md §5.1):
 * a decisão dentro/fora <b>e a magnitude</b> (menor distância ao traçado, em metros).
 *
 * <ul>
 *   <li>{@code INSIDE} — dentro do corredor de algum shape;</li>
 *   <li>{@code OUTSIDE} — fora do corredor de todos; {@code distanceMeters} é a menor
 *       distância ao traçado mais próximo (usada pela margem de confirmação §5.2 e pela
 *       severidade §5.4);</li>
 *   <li>{@code UNRESOLVED} — geometria ainda não em cache / linha sem shapes: a detecção
 *       espera o próximo ciclo ({@code distanceMeters} não é significativo).</li>
 * </ul>
 */
public record RouteAdherence(Status status, double distanceMeters) {

    public enum Status {INSIDE, OUTSIDE, UNRESOLVED}

    private static final RouteAdherence UNRESOLVED = new RouteAdherence(Status.UNRESOLVED, Double.NaN);

    public static RouteAdherence inside(double distanceMeters) {
        return new RouteAdherence(Status.INSIDE, distanceMeters);
    }

    public static RouteAdherence outside(double distanceMeters) {
        return new RouteAdherence(Status.OUTSIDE, distanceMeters);
    }

    public static RouteAdherence unresolved() {
        return UNRESOLVED;
    }

    public boolean isInside() {
        return status == Status.INSIDE;
    }

    public boolean isOutside() {
        return status == Status.OUTSIDE;
    }

    public boolean isUnresolved() {
        return status == Status.UNRESOLVED;
    }
}

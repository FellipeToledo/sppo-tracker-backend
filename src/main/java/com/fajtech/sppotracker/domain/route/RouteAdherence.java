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
 *   <li>{@code NOT_PLANNED} — a fonte respondeu de forma <b>autoritativa</b> que a linha
 *       não tem itinerário no feed vigente. É estado <b>persistente</b>: não adianta
 *       esperar, o dado não existe;</li>
 *   <li>{@code PENDING} — geometria ainda <b>não resolvida</b> (cache frio, resolução em
 *       background, executor saturado). É estado <b>transitório</b>: o próximo ciclo
 *       provavelmente resolve.</li>
 * </ul>
 *
 * <p>Os dois são "não avaliado" para a máquina de desvio ({@link #isUnresolved()}), mas a
 * classificação os separa em tags distintas: confundir "linha sem plano" com "ainda não
 * resolvi" faz um número operacional (quantos veículos estão sem itinerário) inflar a cada
 * reinício. {@code distanceMeters} não é significativo em nenhum dos dois.
 */
public record RouteAdherence(Status status, double distanceMeters) {

    public enum Status {INSIDE, OUTSIDE, NOT_PLANNED, PENDING}

    public RouteAdherence {
        java.util.Objects.requireNonNull(status, "status");
        boolean resolved = status == Status.INSIDE || status == Status.OUTSIDE;
        if (resolved && (!Double.isFinite(distanceMeters) || distanceMeters < 0)) {
            throw new IllegalArgumentException("resolved distance must be finite and non-negative");
        }
    }

    private static final RouteAdherence NOT_PLANNED = new RouteAdherence(Status.NOT_PLANNED, Double.NaN);
    private static final RouteAdherence PENDING = new RouteAdherence(Status.PENDING, Double.NaN);

    public static RouteAdherence inside(double distanceMeters) {
        return new RouteAdherence(Status.INSIDE, distanceMeters);
    }

    public static RouteAdherence outside(double distanceMeters) {
        return new RouteAdherence(Status.OUTSIDE, distanceMeters);
    }

    /** Linha sem itinerário no feed vigente (resposta autoritativa da fonte). */
    public static RouteAdherence notPlanned() {
        return NOT_PLANNED;
    }

    /** Geometria ainda não resolvida — transitório. */
    public static RouteAdherence pending() {
        return PENDING;
    }

    public boolean isInside() {
        return status == Status.INSIDE;
    }

    public boolean isOutside() {
        return status == Status.OUTSIDE;
    }

    /** True para os dois estados sem geometria — o que a máquina de desvio precisa saber. */
    public boolean isUnresolved() {
        return status == Status.NOT_PLANNED || status == Status.PENDING;
    }

    public boolean isNotPlanned() {
        return status == Status.NOT_PLANNED;
    }

    public boolean isPending() {
        return status == Status.PENDING;
    }
}

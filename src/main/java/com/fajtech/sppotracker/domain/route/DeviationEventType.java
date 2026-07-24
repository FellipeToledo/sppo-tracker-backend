package com.fajtech.sppotracker.domain.route;

/**
 * Tipos de evento de desvio emitidos pela máquina de estados
 * (docs/regras-de-negocio.md §5.3).
 *
 * <ul>
 *   <li>{@code ALERT} — provisório (C1): abriu episódio após N pontos fora;</li>
 *   <li>{@code CONFIRMED} — consolidado (C2): sustentado por tempo ou distância;</li>
 *   <li>{@code RETURN} — fechou após CONFIRMED: voltou ao itinerário;</li>
 *   <li>{@code CANCELLED} — fechou sem consolidar (só houve ALERT): transitório.</li>
 * </ul>
 */
public enum DeviationEventType {
    ALERT,
    CONFIRMED,
    RETURN,
    CANCELLED
}

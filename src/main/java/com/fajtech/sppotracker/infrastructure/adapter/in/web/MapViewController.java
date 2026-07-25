package com.fajtech.sppotracker.infrastructure.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Adapter de entrada (view) para o mapa de acompanhamento da frota
 * (docs/regras-de-negocio.md §7). Serve uma página Thymeleaf que recebe as posições
 * <b>em tempo real</b> pelo WebSocket/STOMP ({@code /ws} → {@code /topic/vehicle-positions})
 * e usa o REST ({@code /api/v1/vehicle-positions/current}) para a carga inicial e um
 * reconcile periódico (remover veículos que sumiram). Sem lógica de domínio aqui.
 *
 * @param reconcileSeconds intervalo do reconcile via REST, exposto à página
 */
@Controller
public class MapViewController {

    private static final int DEFAULT_RECONCILE_SECONDS = 30;

    @GetMapping("/")
    public String map(Model model) {
        model.addAttribute("reconcileSeconds", DEFAULT_RECONCILE_SECONDS);
        return "map";
    }
}

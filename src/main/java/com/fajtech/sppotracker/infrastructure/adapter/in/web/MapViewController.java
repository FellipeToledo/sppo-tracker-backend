package com.fajtech.sppotracker.infrastructure.adapter.in.web;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Adapter de entrada (view) para o mapa de acompanhamento da frota
 * (docs/regras-de-negocio.md §7). Serve uma página Thymeleaf que consome os
 * endpoints REST já existentes ({@code /api/v1/vehicle-positions/current}) — sem
 * lógica de domínio aqui. Serve para "ir vendo e ajustando" enquanto o front
 * definitivo não existe.
 *
 * @param refreshSeconds intervalo de auto-atualização, exposto à página
 */
@Controller
public class MapViewController {

    private static final int DEFAULT_REFRESH_SECONDS = 10;

    @GetMapping("/")
    public String map(Model model) {
        model.addAttribute("refreshSeconds", DEFAULT_REFRESH_SECONDS);
        return "map";
    }
}

package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.geofence.GarageArea;

import java.util.List;

/**
 * Porta de saída: polígonos de garagem disponíveis (docs/regras-de-negocio.md §4.3).
 * O dado é estático e empacotado — carregado uma vez na inicialização, fora do hot
 * path. Lista vazia é resposta legítima (a regra fica neutra).
 */
public interface GarageGeofencePort {

    List<GarageArea> garages();
}

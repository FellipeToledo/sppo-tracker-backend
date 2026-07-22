package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.vehicle.ClassifiedVehiclePosition;

/**
 * Porta de saída para publicar um evento de posição (posição mudada, já
 * classificada) no barramento de tempo real (docs/regras-de-negocio.md §3, §7.2,
 * §7.4). A implementação distribui via Pub/Sub para os assinantes WebSocket.
 */
public interface PublishVehiclePositionEventPort {

    void publish(ClassifiedVehiclePosition event);
}

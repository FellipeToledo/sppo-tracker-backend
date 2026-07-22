package com.fajtech.sppotracker.application.port.out;

import com.fajtech.sppotracker.domain.vehicle.VehiclePosition;

import java.time.Instant;
import java.util.List;

/**
 * Porta de saída para buscar posições GPS em uma fonte externa (ex.: API pública
 * da SMTR). A janela é expressa em UTC ({@link Instant}); a conversão para o fuso
 * exigido pela fonte acontece dentro do adapter, na borda.
 *
 * <p>Contrato de robustez: registros malformados são descartados individualmente
 * pelo adapter — nunca derrubam o lote (docs/regras-de-negocio.md §1, §9).
 */
public interface FetchExternalGpsPositionsPort {

    /**
     * Busca as posições reportadas na janela {@code [from, to]}.
     *
     * @param from início da janela (inclusive), em UTC
     * @param to   fim da janela (inclusive), em UTC
     * @return posições válidas encontradas; lista vazia se não houver
     */
    List<VehiclePosition> fetch(Instant from, Instant to);
}

package com.fajtech.sppotracker.infrastructure.adapter.out.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Item cru do array JSON retornado por {@code GET dados.mobilidade.rio/gps/sppo}.
 * Todos os campos chegam como <b>string</b> (ver docs/regras-de-negocio.md §1);
 * a normalização/validação é feita no {@link DadosMobilidadeRioGpsMapper}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DadosMobilidadeRioGpsItem(
        String ordem,
        String latitude,
        String longitude,
        String datahora,
        String velocidade,
        String linha,
        String datahoraenvio,
        String datahoraservidor) {
}

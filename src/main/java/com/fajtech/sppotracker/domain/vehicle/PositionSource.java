package com.fajtech.sppotracker.domain.vehicle;

/**
 * Fonte de origem de uma posição GPS. Abstração mantida para permitir outras
 * fontes além da API pública da SMTR (docs/regras-de-negocio.md §2.1, §3.1).
 */
public enum PositionSource {
    DADOS_MOBILIDADE_RIO
}

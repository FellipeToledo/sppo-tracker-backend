package com.fajtech.sppotracker.infrastructure.adapter.out.gtfs;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Resposta de {@code GET /api/v1/lines/{lineCode}/shapes} do {@code sppo-gtfs-service}
 * (forma encoded). Só os campos consumidos pelo backend são mapeados; o resto é
 * ignorado ({@code ignoreUnknown}) para tolerar evolução do contrato v1.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LineShapesResponse(
        String line,
        FeedVersion feedVersion,
        String resolution,
        List<Shape> shapes) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FeedVersion(String id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Shape(String shapeId, String encodedPolyline) {
    }
}

package com.fajtech.sppotracker.infrastructure.adapter.out.gtfs;

import com.fajtech.sppotracker.domain.vehicle.Coordinates;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodifica o formato <b>Google Encoded Polyline</b> (precisão 5) emitido pelo
 * {@code sppo-gtfs-service} para uma lista de {@link Coordinates}. Algoritmo padrão:
 * deltas em zig-zag, chunks de 5 bits, escala 1e5.
 */
final class EncodedPolyline {

    private static final int SCALE = 5;

    private EncodedPolyline() {
    }

    static List<Coordinates> decode(String encoded) {
        List<Coordinates> points = new ArrayList<>();
        if (encoded == null || encoded.isEmpty()) {
            return points;
        }
        int index = 0;
        int lat = 0;
        int lon = 0;
        int length = encoded.length();
        while (index < length) {
            int[] latResult = decodeValue(encoded, index);
            lat += latResult[0];
            index = latResult[1];
            int[] lonResult = decodeValue(encoded, index);
            lon += lonResult[0];
            index = lonResult[1];
            points.add(new Coordinates(BigDecimal.valueOf(lat, SCALE), BigDecimal.valueOf(lon, SCALE)));
        }
        return points;
    }

    /** Decodifica um valor a partir de {@code index}; devolve {@code [delta, nextIndex]}. */
    private static int[] decodeValue(String encoded, int index) {
        int shift = 0;
        int result = 0;
        int b;
        do {
            b = encoded.charAt(index++) - 63;
            result |= (b & 0x1f) << shift;
            shift += 5;
        } while (b >= 0x20);
        int delta = (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
        return new int[]{delta, index};
    }
}

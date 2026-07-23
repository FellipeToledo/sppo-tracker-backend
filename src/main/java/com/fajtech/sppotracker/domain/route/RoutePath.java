package com.fajtech.sppotracker.domain.route;

import com.fajtech.sppotracker.domain.vehicle.BoundingBox;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Traçado (shape) de um itinerário: sequência ordenada de pontos e a bounding box
 * pré-computada (docs/regras-de-negocio.md §4.4). Value object puro; a geometria é
 * resolvida fora do hot path e testada contra o corredor no momento da classificação.
 *
 * <p>Um ponto está "no corredor" se a distância ao traçado for {@code ≤ thresholdMeters}.
 * A bounding box (dilatada pela tolerância) serve de pré-filtro barato antes da
 * varredura O(n) dos segmentos.
 */
public record RoutePath(String shapeId, List<Coordinates> points, BoundingBox bbox) {

    public RoutePath {
        Objects.requireNonNull(points, "points");
        points = List.copyOf(points);
        if (points.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        Objects.requireNonNull(bbox, "bbox");
    }

    /** Cria um traçado computando a bounding box a partir dos pontos. */
    public static RoutePath of(String shapeId, List<Coordinates> points) {
        List<Coordinates> copy = List.copyOf(points);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("points must not be empty");
        }
        BigDecimal minLat = copy.getFirst().latitude();
        BigDecimal maxLat = minLat;
        BigDecimal minLon = copy.getFirst().longitude();
        BigDecimal maxLon = minLon;
        for (Coordinates c : copy) {
            minLat = minLat.min(c.latitude());
            maxLat = maxLat.max(c.latitude());
            minLon = minLon.min(c.longitude());
            maxLon = maxLon.max(c.longitude());
        }
        return new RoutePath(shapeId, copy, new BoundingBox(minLat, maxLat, minLon, maxLon));
    }

    /** True se o ponto cai no corredor de tolerância {@code thresholdMeters} deste traçado. */
    public boolean withinCorridor(Coordinates p, double thresholdMeters) {
        if (!nearBoundingBox(p, thresholdMeters)) {
            return false;
        }
        return distanceMeters(p) <= thresholdMeters;
    }

    /** Menor distância, em metros, do ponto ao traçado (ponto único ou polilinha). */
    public double distanceMeters(Coordinates p) {
        double pLat = p.latitude().doubleValue();
        double pLon = p.longitude().doubleValue();
        if (points.size() == 1) {
            Coordinates only = points.getFirst();
            return Geo.metersBetween(pLat, pLon, only.latitude().doubleValue(), only.longitude().doubleValue());
        }
        double best = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Coordinates a = points.get(i);
            Coordinates b = points.get(i + 1);
            double d = Geo.pointToSegmentMeters(pLat, pLon,
                    a.latitude().doubleValue(), a.longitude().doubleValue(),
                    b.latitude().doubleValue(), b.longitude().doubleValue());
            if (d < best) {
                best = d;
                if (best == 0.0) {
                    break;
                }
            }
        }
        return best;
    }

    private boolean nearBoundingBox(Coordinates p, double marginMeters) {
        double lat = p.latitude().doubleValue();
        double lon = p.longitude().doubleValue();
        double marginLat = marginMeters / Geo.METERS_PER_DEG_LAT;
        double cos = Math.cos(Math.toRadians(lat));
        double marginLon = cos == 0.0 ? 180.0 : marginMeters / (Geo.METERS_PER_DEG_LAT * cos);
        return lat >= bbox.minLatitude().doubleValue() - marginLat
                && lat <= bbox.maxLatitude().doubleValue() + marginLat
                && lon >= bbox.minLongitude().doubleValue() - marginLon
                && lon <= bbox.maxLongitude().doubleValue() + marginLon;
    }
}

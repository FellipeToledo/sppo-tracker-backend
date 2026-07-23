package com.fajtech.sppotracker.domain.route;

/**
 * Utilitários geográficos para o teste de corredor (docs/regras-de-negocio.md §4.4).
 * Distâncias em metros. Para segmentos de escala urbana usa-se projeção
 * equirretangular local (rápida e precisa o suficiente para tolerâncias de ~15 m);
 * a distância ponto-a-ponto usa haversine.
 */
final class Geo {

    static final double EARTH_RADIUS_M = 6_371_000.0;
    static final double METERS_PER_DEG_LAT = 111_320.0;

    private Geo() {
    }

    /** Distância haversine entre dois pontos, em metros. */
    static double metersBetween(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }

    /**
     * Distância, em metros, do ponto {@code p} ao segmento {@code a–b}, projetando
     * para um plano local em metros ancorado em {@code a}.
     */
    static double pointToSegmentMeters(double pLat, double pLon,
                                       double aLat, double aLon,
                                       double bLat, double bLon) {
        double mPerDegLon = METERS_PER_DEG_LAT * Math.cos(Math.toRadians((aLat + bLat) / 2.0));
        double bx = (bLon - aLon) * mPerDegLon;
        double by = (bLat - aLat) * METERS_PER_DEG_LAT;
        double px = (pLon - aLon) * mPerDegLon;
        double py = (pLat - aLat) * METERS_PER_DEG_LAT;
        double len2 = bx * bx + by * by;
        double t = len2 == 0.0 ? 0.0 : (px * bx + py * by) / len2;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = t * bx;
        double cy = t * by;
        return Math.hypot(px - cx, py - cy);
    }
}

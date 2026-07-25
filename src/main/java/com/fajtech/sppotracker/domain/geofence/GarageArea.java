package com.fajtech.sppotracker.domain.geofence;

import com.fajtech.sppotracker.domain.vehicle.BoundingBox;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Área de garagem: polígono simples (anel externo, sem buracos) com a bounding box
 * pré-computada (docs/regras-de-negocio.md §4.3). Value object puro — os polígonos
 * vêm do GeoJSON empacotado, carregado uma vez na inicialização.
 *
 * <p>O teste de pertinência é <b>ponto-em-polígono por ray casting</b>, com a bbox
 * como pré-filtro barato (o hot path avalia toda posição contra todas as áreas).
 * Ray casting — e não bounding box nem convex hull — porque pátio real é côncavo:
 * a bbox de um pátio em "L" incluiria a rua vizinha.
 *
 * <p><b>Borda conta como dentro</b> (decisão explícita): o ray casting é indefinido
 * exatamente na aresta, e para garagem o conservador é classificar dentro.
 *
 * @param address      endereço, opcional — apenas descritivo (popup/endpoint §7.1);
 *                     nenhuma regra depende dele
 * @param neighborhood bairro, opcional — idem
 * @param ring         anel fechado (primeiro ponto == último); {@link #of} fecha se preciso
 * @param bounds       derivada do anel — o valor passado ao construtor é ignorado por design
 */
public record GarageArea(String id, String name, String address, String neighborhood,
                         List<Coordinates> ring, BoundingBox bounds) {

    /** Tolerância em graus (~0,1 mm) para considerar o ponto sobre a aresta. */
    private static final double ON_EDGE_EPSILON = 1e-9;

    public GarageArea {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        Objects.requireNonNull(ring, "ring");
        ring = closed(List.copyOf(ring));
        bounds = computeBounds(ring);
        id = id.trim();
        name = name.trim();
        address = blankToNull(address);
        neighborhood = blankToNull(neighborhood);
    }

    /** Cria a área fechando o anel (se preciso) e derivando a bounding box. */
    public static GarageArea of(String id, String name, List<Coordinates> ring) {
        return new GarageArea(id, name, null, null, ring, null);
    }

    /** Idem, com os campos descritivos do dataset (endereço e bairro). */
    public static GarageArea of(String id, String name, String address, String neighborhood,
                                List<Coordinates> ring) {
        return new GarageArea(id, name, address, neighborhood, ring, null);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static List<Coordinates> closed(List<Coordinates> ring) {
        long distinct = ring.stream().distinct().count();
        if (distinct < 3) {
            throw new IllegalArgumentException("ring must have at least 3 distinct points");
        }
        if (ring.getFirst().equals(ring.getLast())) {
            return ring;
        }
        List<Coordinates> closed = new ArrayList<>(ring);
        closed.add(ring.getFirst());
        return List.copyOf(closed);
    }

    private static BoundingBox computeBounds(List<Coordinates> ring) {
        BigDecimal minLat = ring.getFirst().latitude();
        BigDecimal maxLat = minLat;
        BigDecimal minLon = ring.getFirst().longitude();
        BigDecimal maxLon = minLon;
        for (Coordinates c : ring) {
            minLat = minLat.min(c.latitude());
            maxLat = maxLat.max(c.latitude());
            minLon = minLon.min(c.longitude());
            maxLon = maxLon.max(c.longitude());
        }
        return new BoundingBox(minLat, maxLat, minLon, maxLon);
    }

    /** True se a posição está dentro do polígono (ou sobre a sua borda). */
    public boolean contains(Coordinates position) {
        Objects.requireNonNull(position, "position");
        if (!bounds.contains(position)) {
            return false;
        }
        double y = position.latitude().doubleValue();
        double x = position.longitude().doubleValue();
        if (onEdge(x, y)) {
            return true;
        }
        boolean inside = false;
        for (int i = 0; i < ring.size() - 1; i++) {
            double y1 = ring.get(i).latitude().doubleValue();
            double x1 = ring.get(i).longitude().doubleValue();
            double y2 = ring.get(i + 1).latitude().doubleValue();
            double x2 = ring.get(i + 1).longitude().doubleValue();
            // Aresta cruza a horizontal do ponto? Comparação semiaberta em y, para
            // não contar duas vezes o vértice compartilhado por duas arestas.
            if ((y1 > y) != (y2 > y)) {
                double xAtY = x1 + (y - y1) * (x2 - x1) / (y2 - y1);
                if (x < xAtY) {
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private boolean onEdge(double x, double y) {
        for (int i = 0; i < ring.size() - 1; i++) {
            double y1 = ring.get(i).latitude().doubleValue();
            double x1 = ring.get(i).longitude().doubleValue();
            double dx = ring.get(i + 1).longitude().doubleValue() - x1;
            double dy = ring.get(i + 1).latitude().doubleValue() - y1;
            double lengthSquared = dx * dx + dy * dy;
            if (lengthSquared == 0.0) {
                // Vértice repetido: degenera em ponto.
                if (Math.hypot(x - x1, y - y1) <= ON_EDGE_EPSILON) {
                    return true;
                }
                continue;
            }
            // Projeção normalizada: fora de [0,1] o ponto não está sobre ESTE segmento
            // (um vértice compartilhado é coberto pela aresta vizinha).
            double t = ((x - x1) * dx + (y - y1) * dy) / lengthSquared;
            if (t < 0.0 || t > 1.0) {
                continue;
            }
            // Distância perpendicular REAL, em graus. Dividir pelo comprimento é o
            // ponto crítico: o produto vetorial está em graus², então compará-lo direto
            // com a tolerância dava folga proporcional a 1/comprimento — numa aresta de
            // 0,83 m (existe no dataset real) a folga virava ~15 m, e ônibus fora do lote
            // era classificado como dentro da garagem.
            double distance = Math.abs(dx * (y - y1) - dy * (x - x1)) / Math.sqrt(lengthSquared);
            if (distance <= ON_EDGE_EPSILON) {
                return true;
            }
        }
        return false;
    }
}

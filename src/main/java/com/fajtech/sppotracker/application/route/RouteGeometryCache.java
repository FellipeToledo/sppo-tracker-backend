package com.fajtech.sppotracker.application.route;

import com.fajtech.sppotracker.application.port.out.ResolveRouteShapesPort;
import com.fajtech.sppotracker.domain.route.LineCodeKey;
import com.fajtech.sppotracker.domain.route.RouteGeometrySource;
import com.fajtech.sppotracker.domain.route.RoutePath;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

/**
 * Cache assíncrono, bounded e não-bloqueante de geometria de itinerário
 * (docs/regras-de-negocio.md §4.4). Implementa {@link RouteGeometrySource}: o hot
 * path só lê o que já está resolvido; a resolução real ({@link ResolveRouteShapesPort},
 * I/O) roda em background num {@link Executor} limitado.
 *
 * <p>Comportamento:
 * <ul>
 *   <li>{@code pathsFor} nunca bloqueia — devolve a geometria em cache (ou vazio) e,
 *       se ausente/expirada, agenda uma atualização em background;</li>
 *   <li>enquanto atualiza, serve o valor antigo (stale-while-revalidate);</li>
 *   <li>uma resolução por chave por vez (dedup {@code inFlight}); sob pico, se o
 *       executor rejeitar, a tentativa é descartada e re-tentada no próximo tick;</li>
 *   <li>resultados autoritativos vazios também são cacheados (evita marteladas em
 *       linhas sem geometria).</li>
 * </ul>
 */
public final class RouteGeometryCache implements RouteGeometrySource {

    private final ResolveRouteShapesPort port;
    private final Executor refreshExecutor;
    private final Clock clock;
    private final Duration ttl;

    private final Map<String, Entry> cache = new ConcurrentHashMap<>();
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    public RouteGeometryCache(ResolveRouteShapesPort port, Executor refreshExecutor,
                              Clock clock, Duration ttl) {
        this.port = Objects.requireNonNull(port, "port");
        this.refreshExecutor = Objects.requireNonNull(refreshExecutor, "refreshExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    @Override
    public List<RoutePath> pathsFor(String serviceCode) {
        String key = LineCodeKey.of(serviceCode);
        if (key == null) {
            return List.of();
        }
        Entry entry = cache.get(key);
        if (entry == null || isStale(entry)) {
            scheduleRefresh(serviceCode, key);
        }
        return entry == null ? List.of() : entry.paths();
    }

    private boolean isStale(Entry entry) {
        return Duration.between(entry.resolvedAt(), clock.instant()).compareTo(ttl) >= 0;
    }

    private void scheduleRefresh(String rawServiceCode, String key) {
        if (!inFlight.add(key)) {
            return;
        }
        try {
            refreshExecutor.execute(() -> {
                try {
                    port.resolve(rawServiceCode).ifPresent(resolved ->
                            cache.put(key, new Entry(resolved.paths(), clock.instant(), resolved.feedVersionId())));
                } finally {
                    inFlight.remove(key);
                }
            });
        } catch (RejectedExecutionException rejected) {
            // Executor saturado (§4.4): descarta e re-tenta no próximo tick.
            inFlight.remove(key);
        }
    }

    private record Entry(List<RoutePath> paths, Instant resolvedAt, String feedVersionId) {
    }
}

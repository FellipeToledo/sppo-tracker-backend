package com.fajtech.sppotracker.application.route;

import com.fajtech.sppotracker.application.port.out.ResolveRouteShapesPort;
import com.fajtech.sppotracker.domain.route.RoutePath;
import com.fajtech.sppotracker.domain.vehicle.Coordinates;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class RouteGeometryCacheTest {

    // Executor síncrono (caller-runs) → o refresh agendado roda antes de execute() retornar,
    // tornando o comportamento do cache determinístico no teste.
    private static final java.util.concurrent.Executor DIRECT = Runnable::run;

    private static RoutePath segment() {
        return RoutePath.of("s1", List.of(
                new Coordinates(BigDecimal.valueOf(-22.9000), BigDecimal.valueOf(-43.2000)),
                new Coordinates(BigDecimal.valueOf(-22.9000), BigDecimal.valueOf(-43.1900))));
    }

    @Test
    void shouldReturnEmptyOnFirstMissThenServeResolvedGeometry() {
        AtomicInteger calls = new AtomicInteger();
        ResolveRouteShapesPort port = service -> {
            calls.incrementAndGet();
            return Optional.of(new ResolvedShapes("2026-07", List.of(segment())));
        };
        RouteGeometryCache cache = new RouteGeometryCache(port, DIRECT, Clock.systemUTC(), Duration.ofHours(6));

        assertThat(cache.pathsFor("100")).isEmpty();      // dispara o refresh em background
        assertThat(cache.pathsFor("100")).hasSize(1);      // já resolvido em cache
        assertThat(calls.get()).isEqualTo(1);              // uma única resolução
    }

    @Test
    void shouldNotCacheOnTransientFailure() {
        AtomicInteger calls = new AtomicInteger();
        ResolveRouteShapesPort port = service -> {
            calls.incrementAndGet();
            return Optional.empty();                        // falha transitória
        };
        RouteGeometryCache cache = new RouteGeometryCache(port, DIRECT, Clock.systemUTC(), Duration.ofHours(6));

        assertThat(cache.pathsFor("100")).isEmpty();
        assertThat(cache.pathsFor("100")).isEmpty();
        assertThat(calls.get()).isEqualTo(2);              // re-tenta a cada miss
    }

    @Test
    void shouldRefreshAfterTtlExpires() {
        AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-07-23T12:00:00Z"));
        Clock clock = new MovableClock(now);
        AtomicInteger calls = new AtomicInteger();
        ResolveRouteShapesPort port = service -> {
            calls.incrementAndGet();
            return Optional.of(new ResolvedShapes("2026-07", List.of(segment())));
        };
        RouteGeometryCache cache = new RouteGeometryCache(port, DIRECT, clock, Duration.ofHours(6));

        cache.pathsFor("100");                              // resolve #1
        assertThat(cache.pathsFor("100")).hasSize(1);
        assertThat(calls.get()).isEqualTo(1);

        now.set(now.get().plus(Duration.ofHours(7)));       // passa o TTL
        assertThat(cache.pathsFor("100")).hasSize(1);       // serve stale e re-resolve
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test
    void shouldTreatDifferentLeadingZeroCodesAsSameCacheKey() {
        AtomicInteger calls = new AtomicInteger();
        ResolveRouteShapesPort port = service -> {
            calls.incrementAndGet();
            return Optional.of(new ResolvedShapes("2026-07", List.of(segment())));
        };
        RouteGeometryCache cache = new RouteGeometryCache(port, DIRECT, Clock.systemUTC(), Duration.ofHours(6));

        cache.pathsFor("0100");
        assertThat(cache.pathsFor("100")).hasSize(1);        // mesma chave normalizada
        assertThat(calls.get()).isEqualTo(1);
    }

    private static final class MovableClock extends Clock {
        private final AtomicReference<Instant> now;

        private MovableClock(AtomicReference<Instant> now) {
            this.now = now;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }
}

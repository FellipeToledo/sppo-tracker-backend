-- Histórico de eventos de desvio de itinerário (docs/regras-de-negocio.md §5, §7.4).
-- Esqueleto de persistência: a máquina de estados de desvio (§5) depende de
-- geometria de shapes (indisponível com o feed público) e entra numa fatia futura;
-- esta tabela recebe os eventos emitidos (ALERT/CONFIRMED/RETURN/CANCELLED).
CREATE TABLE route_deviation_event (
    id               BIGSERIAL PRIMARY KEY,
    vehicle_id       VARCHAR(32)      NOT NULL,
    route_id         VARCHAR(32),
    event_type       VARCHAR(16)      NOT NULL,
    severity         VARCHAR(8),
    distance_meters  DOUBLE PRECISION,
    occurred_at      TIMESTAMPTZ      NOT NULL,
    created_at       TIMESTAMPTZ      NOT NULL DEFAULT now()
);

CREATE INDEX idx_route_deviation_event_vehicle_time
    ON route_deviation_event (vehicle_id, occurred_at);

-- Máquina de desvio (docs/regras-de-negocio.md §5): com o feed público a linha é
-- identificada pelo serviço (serviceCode), não por routeId. Guardamos ambos: routeId
-- quando disponível e service_code para a linha efetiva do episódio.
ALTER TABLE route_deviation_event ADD COLUMN service_code VARCHAR(32);

CREATE INDEX idx_route_deviation_event_service_time
    ON route_deviation_event (service_code, occurred_at);

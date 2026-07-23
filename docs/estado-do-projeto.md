# Estado do projeto — sppo-tracker-backend

Documento de **handoff** entre sessões. Resume o que está pronto, o que foi
deliberadamente adiado e o débito técnico em aberto. Atualize-o ao fim de cada
bloco de trabalho.

> Fonte da verdade do domínio: **`docs/regras-de-negocio.md`**. Convenções e
> comandos: **`CLAUDE.md`**. Este arquivo é só o "onde paramos".

---

## 1. Visão geral do que existe

Backend Java 21 / Spring Boot 4.1 (Jackson 3), arquitetura hexagonal, que faz polling da API
pública de GPS do SPPO (SMTR), classifica as posições e as distribui via REST,
WebSocket e métricas Prometheus. **Hot path completo e testado:**

```
scheduler → readiness → cooldown → janela [now-90s, now] → fetch (provider)
  → dedup (Redis) → detecção de mudança → classificação → snapshot (Redis)
  → publica evento (Redis Pub/Sub → STOMP) → métricas
```

- **Migração Spring Boot 4.1 / Jackson 3 concluída** (após os merges do Dependabot):
  imports `com.fasterxml.jackson.databind` → `tools.jackson.databind`, exceções
  Jackson agora *unchecked* (`JacksonException`), `TypeReference` do Jackson 3,
  mappers de teste via `JsonMapper.builder()`, e a dependência de teste
  `spring-boot-starter-webmvc-test` (o `@WebMvcTest` mudou para
  `org.springframework.boot.webmvc.test.autoconfigure`). Annotations Jackson
  permanecem em `com.fasterxml.jackson.annotation`.
- **Testes:** `mvn --batch-mode test` → **130 testes, 0 falhas** (todos herméticos:
  sem Redis/Postgres/rede reais — usam mocks, `SimpleMeterRegistry`, MockWebServer,
  `@WebMvcTest`).
- **Branch de trabalho:** `claude/sppo-tracker-setup-cd7vmo`. **Nenhum PR aberto**
  ainda (abrir só quando desejado).

## 2. Roadmap — fatias concluídas (1–10)

| # | Fatia | Entregue |
|---|-------|----------|
| 1 | Domínio | `VehiclePosition` (+invariantes, Builder), `PositionSource` |
| 2 | Provider | `FetchExternalGpsPositionsPort` + adapter WebClient (SMTR); `DecimalParser`/`EpochParser`; mapper defensivo |
| 3 | Ingestão/scheduler | `GpsPollingService` (readiness, cooldown, janela de sobreposição), `GpsPollingScheduler`, `PublicApiProviderReadiness` |
| 4 | Dedup + mudança | `DeduplicationPort`/`RedisDeduplicationStore` (SET NX EX), `PositionChangeDetector` |
| 5 | Snapshot + pipeline | `CurrentSnapshotStorePort`/`RedisCurrentSnapshotStore`, `GpsPositionIngestor` |
| 6 | Classificação | `PositionClassifier` (precedência §4.2), regras, `PositionClassification` (flags §4.5), `ClassifiedVehiclePosition` |
| 7 | REST v1 | `GET /api/v1/vehicle-positions/current` (filtros), `GET /api/v1/gps-polling/status`, `application/problem+json` |
| 8 | WebSocket/STOMP | `/ws`, tópicos `/topic/vehicle-positions[/service|/route]`, relay Redis Pub/Sub→STOMP |
| 9 | Métricas | Micrometer em polling, provider e classificação (`/actuator/prometheus`) |
| 10 | Operadoras + persistência | `/api/v1/operators`, rótulo por veículo; esqueleto Flyway/Postgres (tabela + entidade + repo de eventos de desvio) |

## 3. Endpoints disponíveis

- `GET /api/v1/vehicle-positions/current?serviceCode=&routeId=&classificationStatus=`
- `GET /api/v1/gps-polling/status` (204 se nenhum ciclo rodou)
- `GET /api/v1/operators`
- WebSocket STOMP: `/ws` → assinar `/topic/vehicle-positions`
  (e `/service/{serviceCode}`, `/route/{routeId}`)
- `GET /actuator/health`, `GET /actuator/prometheus`

Métricas: `gps.polling.cycles{outcome}`, `gps.polling.cycle.duration`,
`gps.polling.consecutive.failures`, `gps.ingestion.positions{result}`,
`gps.classifications{status}`, `gps.position.age`,
`gps.provider.requests{outcome}`, `gps.provider.request.duration`,
`gps.provider.window.seconds`.

---

## 4. Trabalho ADIADO (bloqueado por dados que o feed público não tem)

O feed público da SMTR **não fornece** `routeId`/`shapeId`/sentido, nem shapes ou
polígonos de garagem. Por isso ficaram fora:

- **§5 — Máquina de estados de desvio de rota (C1 ALERT / C2 CONFIRMED / RETURN /
  CANCELLED), severidade, sweep.** Depende de aderência dentro/fora do corredor
  (geometria de shapes). **A persistência já está pronta** para receber os eventos
  (`route_deviation_event`, entidade e repositório existem; falta o *escritor*).
- **Regra `OUT_OF_ROUTE`** — sem produtor (§4.4 diz que fica desligada com o feed
  público). A tag e o lugar na precedência/flags já existem no modelo.
- **Regra `GARAGE_GEOFENCE`** — precisa de polígonos GeoJSON de garagem
  (empacotados). A tag já existe; `IN_GARAGE` hoje só é atingido por código de
  serviço.
- **Endpoints §7.1 não implementados:** `/api/v1/garages`,
  `/api/v1/shapes/{id}`, `/api/v1/shapes/{id}/corridor` (dependem de geometria).

**Para destravar §5/OUT_OF_ROUTE/geofence:** decidir a fonte de itinerário
(GTFS/`shape-geom` próprio) — ver §10 e TODO de `docs/regras-de-negocio.md`.

## 5. DÉBITO TÉCNICO

1. **[ENDEREÇADO] Teste de contexto Spring.** Adicionado
   `SppoTrackerBackendApplicationTests` (`@SpringBootTest contextLoads`,
   hermético — infra Redis/Postgres desligada por `spring.autoconfigure.exclude`
   e beans Redis mockados). Roda no `mvn test`. *Pegou um bug real:* o
   `WebClient.Builder` não é autoconfigurado no Boot 4 (servlet) — agora fornecido
   explicitamente em `ProviderConfig`.
2. **[ENDEREÇADO na CI] Fidelidade Redis/Postgres.** ITs Testcontainers
   (sufixo `*IntegrationTest`, `@Tag("integration")`):
   `RedisDeduplicationStoreIntegrationTest` (SET NX EX + TTL),
   `RedisCurrentSnapshotStoreIntegrationTest` (JSON + SCAN),
   `RouteDeviationEventRepositoryIntegrationTest` (Flyway V1 + JPA em Postgres real).
   Surefire (`mvn test`) exclui a tag `integration`; Failsafe (`mvn verify`) roda os
   `*IntegrationTest` — a **CI do GitHub Actions** os executa (tem Docker).
   ⚠️ **Não foram executados nesta sessão**: o egress bloqueia o pull de imagens
   Docker aqui; a verificação vem da CI do PR.
3. **[ENDEREÇADO] De-para de operadoras populado com dados reais (dois níveis).**
   Saiu do dataset *starter* (prefixo de 4 chars fictício) para **dois de-paras
   reais**: `consortiums.json` (1º caractere da ordem → consórcio; `A`→Intersul,
   `B`→Internorte, `C`→Transcarioca, `D`→Santa Cruz — cobre toda a frota) e
   `companies.json` (prefixo de 4 chars → empresa real da SMTR, ex.: `A410`→Real
   Auto Onibus; cobertura parcial). Modelo de domínio: `Consortium`, `Company` e
   `VehicleOperator`; a API expõe `consortiumName`+`companyName` por veículo e
   `GET /api/v1/operators` lista empresas anotadas com o consórcio. Ver
   `docs/regras-de-negocio.md` §6. *Manutenção:* atualizar `companies.json`
   conforme novos prefixos entrem em operação (o consórcio nunca fica sem rótulo).

## 6. Convenções seguidas (para manter na próxima sessão)

- **TDD:** teste que falha primeiro → implementar → `mvn test`.
- **Hexagonal estrito:** `domain/` puro; `application/` depende só de portas e é
  **livre de framework** (serviços instanciados como `@Bean` pela infra, não
  `@Service`); `infrastructure/` implementa as portas.
- **Commits:** Conventional Commits, mensagens em inglês, pequenos por fatia.
- **Fluxo:** plan mode → implementar → `mvn test` verde → `/security-review` quando
  toca I/O → commit → push.
- **Tempo** em UTC interno (`Clock` injetável); conversão só na borda (filtro da API
  em BRT).

## 7. Próximos passos sugeridos (ordem de valor/viabilidade)

1. **Fechar débito técnico** (escolher (a) Testcontainers, (b) `contextLoads` leve,
   ou (c) manter como TODO).
2. ~~**Popular `operators.json`** com dados reais.~~ **Feito** (de-para por
   consórcio, 1º caractere da ordem — ver §5, débito 3).
3. **Deploy** na VM Oracle Cloud (docker compose; ver §10 de
   `docs/regras-de-negocio.md` — atenção aos dois firewalls).
4. **Fonte de shapes** (GTFS/shape-geom) para destravar §5 / OUT_OF_ROUTE / geofence.
5. **CI** (GitHub Actions: build/test → imagem → deploy).

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
- **Testes:** `mvn --batch-mode test` → **188 testes, 0 falhas** (todos herméticos:
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

- `GET /` — **mapa de acompanhamento** (view Thymeleaf + Leaflet), **tempo real via
  WebSocket/STOMP** (`/ws` → `/topic/vehicle-positions`, marcadores reutilizados por
  `vehicleId`): carga inicial + reconcile periódico pelo REST, updates ao vivo pelo WS.
  Colorido por status, filtro client-side por linha/status, indicador de conexão.
- `GET /api/v1/vehicle-positions/current?serviceCode=&routeId=&classificationStatus=`
- `GET /api/v1/gps-polling/status` (204 se nenhum ciclo rodou)
- `GET /api/v1/operators`
- WebSocket STOMP: `/ws` → assinar `/topic/vehicle-positions`
  (e `/service/{serviceCode}`, `/route/{routeId}`); `/topic/route-deviations`
  (e `/route/{routeId}`) para eventos de desvio (§9)
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

- ~~**§5 — Máquina de estados de desvio de rota.**~~ **FEITO (fatia B — ver §9).**
  Máquina pura (ALERT/CONFIRMED/RETURN/CANCELLED + severidade + sweep), *escritor*
  Postgres, publisher Redis→STOMP `/topic/route-deviations`. Desligada com a fonte
  de shapes off (default).
- ~~**Regra `OUT_OF_ROUTE`** — sem produtor.~~ **FEITO (fatia A):** produtor
  ligado via `sppo-gtfs-service` (repo separado, BigQuery/GTFS da SMTR). Fica
  **desligado por default** (`ROUTE_SHAPE_SOURCE=disabled`); com `gtfs-service`,
  a `OutOfRouteRule` entra no classificador. Detalhes na §8.
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
3. ~~**Deploy** na VM Oracle Cloud (docker compose).~~ **Artefatos prontos:**
   `Dockerfile`, `docker-compose.yml`, `docker-compose.oracle.yml`, `.env.example`
   e o guia **`docs/deploy-oracle-cloud.md`** (inclui os dois firewalls e HTTPS
   opcional via Caddy). Falta só provisionar a VM e rodar.
4. ~~**Fonte de shapes** para destravar OUT_OF_ROUTE.~~ **Feito (fatia A)** — ver §8.
   Falta destravar **§5 (máquina de desvio)** reusando a mesma geometria, e a
   **geofence de garagem**.
5. **CI** (GitHub Actions: build/test → imagem → deploy).

---

## 8. Fatia A — aderência de rota via `sppo-gtfs-service` (OUT_OF_ROUTE)

Integração com o **`sppo-gtfs-service`** (microserviço separado, Java/Spring
hexagonal, que serve shapes do GTFS da SMTR a partir do BigQuery). O backend passou
a **produzir** a tag `OUT_OF_ROUTE`, testando cada posição contra o corredor dos
shapes da linha (`serviceCode`).

**Peças novas:**
- **Domínio (`domain/route`):** `RoutePath` (polilinha + bbox + teste de corredor
  com pré-filtro por bounding box), `Geo` (haversine + distância ponto→segmento),
  `RouteGeometrySource` (porta pura, não-bloqueante), `LineCodeKey` (normalização
  **idêntica** ao `LineCode` do serviço — trim/upper + strip de zeros à esquerda).
  `OutOfRouteRule` (§4.4): fora do corredor de **todos** os shapes ⇒ tag; sem
  geometria ⇒ neutra. A precedência segue fixa no `PositionClassifier`.
- **Aplicação:** `ResolveRouteShapesPort` (out), `ResolvedShapes`, `RouteGeometryCache`
  — cache assíncrono **bounded** e stale-while-revalidate: o hot path só lê o que já
  está resolvido; a resolução (I/O) roda em executor de background; sob pico o excesso
  é descartado e re-tentado (§4.4).
- **Infra:** `SppoGtfsShapeProvider` (WebClient → `GET /api/v1/lines/{lineCode}/shapes`,
  decodifica encoded polyline; 404/no_shapes ⇒ vazio autoritativo; falha ⇒ retry),
  `EncodedPolyline` (decoder Google precisão 5), `RouteAdherenceConfig` +
  `RouteAdherenceProperties` (`gps.route`) + `SppoGtfsClientProperties` (`gps.gtfs-service`).

**Flag / default:** `ROUTE_SHAPE_SOURCE=disabled` por padrão — nenhum bean HTTP é
criado e a regra não é montada (comportamento idêntico ao atual). Com
`gtfs-service`, aponta-se `SPPO_GTFS_BASE_URL` (e `SPPO_GTFS_API_KEY` se houver).
Métrica: `gps.route.shape.resolve{result=shapes|empty|failure}`.

**Débito/known-gaps desta fatia:**
- Cache por **TTL** (6h). O contrato do serviço oferece `ETag`/`304` + `feedVersion.id`;
  a revalidação por ETag ainda **não** foi implementada (melhoria de eficiência, não
  bloqueia). 
- Sem endpoints §7.1 (`/shapes/{id}`, `/corridor`) — fatia C.
- `sppo-gtfs-service` ainda não hospedado (Oracle a provisionar); rodar em `demo`
  para testes ponta a ponta locais.

---

## 9. Fatia B — máquina de desvio de itinerário (§5)

Detecção de episódios de desvio, **ortogonal à classificação** e **fora do hot path**.

**B1 — núcleo puro (`domain/route`):**
- `RouteAdherenceEvaluator` — lógica **única** dentro/fora **+ distância**, reusada pela
  `OutOfRouteRule` e pela máquina (fecha o TODO §10 de unificação).
- `RouteDeviationDetector` — máquina de estados por veículo: `ALERT` (N pontos fora) →
  `CONFIRMED` (sustentado por tempo **ou** distância) → `RETURN`/`CANCELLED`; `sweep`
  consolida veículos silenciosos. "Efetivamente fora" = fora **e** distância > margem
  (§5.2). Enums, `DeviationSeverity` (§5.4), `VehicleDeviationState`, `DeviationConfig`,
  `RouteDeviationEvent`, `DeviationOutcome`. Tudo coberto por teste.

**B2 — wiring (fora do hot path):**
- `RouteDeviationService` (aplicação) — cache de estado por veículo (TTL 6h, poda no
  sweep), transições serializadas por `compute`, I/O de persistência/publicação fora do
  lock. Alimentado pelo `RouteDeviationPositionSubscriber` (consome o canal Redis de
  posições, mesmo payload do REST) — nada é adicionado ao pipeline de ingestão.
- Saída: `JpaRouteDeviationEventWriter` → `route_deviation_event` (migração **V2** adiciona
  `service_code`); `RedisRouteDeviationEventPublisher` → canal Redis → `RouteDeviationEventRelay`
  → STOMP `/topic/route-deviations` [`/route/{routeId}`].
- `RouteDeviationSweepScheduler` (`@Scheduled`, 30s). Config `gps.route.deviation.*`
  (`RouteDeviationProperties`).

**Flag / default:** gated por `gps.route.shape-source=gtfs-service` (depende de geometria)
**e** `gps.route.deviation.enabled` (default true). Com a fonte **disabled** (default),
nenhum bean é criado — comportamento inalterado.

**Known-gaps desta fatia:**
- Sem endpoint REST de histórico de desvios (a tabela é escrita; falta expor `GET`).
- Sem métrica `gps.route.deviations{type}` ainda (observabilidade — follow-up).
- Observação = posições **mudadas** (o stream publicado já é pós-dedup/mudança); posições
  idênticas repetidas não contam como novos pontos.
- Não exercitado com `shape-source=gtfs-service` em teste hermético (precisa de
  Redis/JPA/WebClient) — validação vem da CI/integração ou do run local.

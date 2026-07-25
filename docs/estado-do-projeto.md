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
- **Testes:** `mvn --batch-mode test` → **241 testes, 0 falhas** (todos herméticos:
  sem Redis/Postgres/rede reais — usam mocks, `SimpleMeterRegistry`, MockWebServer,
  `@WebMvcTest`). Os de integração (Testcontainers) rodam no `mvn verify` / CI.
- **Estado do git (2026-07-25):** `main` tem as fatias A/B/C + stack de deploy (PR #11
  mergeado) e a higiene pré-feature. A **geofence de garagem** (§10) está na branch
  `feat/garage-geofence`, pronta para PR.
- **Repo irmão:** **`sppo-gtfs-service`** (fonte da verdade da geometria, BigQuery/GTFS
  da SMTR) — também em `main`, 34 testes verdes. O stack unificado espera os dois
  repos clonados lado a lado (`docs/deploy-stack.md`).

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
  (traz `garageName` quando a geofence de garagem casou — §10)
- `GET /api/v1/gps-polling/status` (204 se nenhum ciclo rodou)
- `GET /api/v1/operators`
- `GET /api/v1/garages` — geofences de garagem em **GeoJSON** (§10)
- `GET /api/v1/route-deviations?vehicleId=&serviceCode=&type=&severity=&limit=` (histórico §5)
- WebSocket STOMP: `/ws` → assinar `/topic/vehicle-positions`
  (e `/service/{serviceCode}`, `/route/{routeId}`); `/topic/route-deviations`
  (e `/route/{routeId}`) para eventos de desvio (§9)
- `GET /actuator/health`, `GET /actuator/prometheus`

Métricas: `gps.polling.cycles{outcome}`, `gps.polling.cycle.duration`,
`gps.polling.consecutive.failures`, `gps.ingestion.positions{result}`,
`gps.classifications{status}`, `gps.position.age`,
`gps.provider.requests{outcome}`, `gps.provider.request.duration`,
`gps.provider.window.seconds`, `gps.route.deviations{type,severity}`,
`gps.route.shape.resolve{result}`.

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
- ~~**Regra `GARAGE_GEOFENCE`** — precisa de polígonos GeoJSON de garagem.~~
  **FEITA (fatia D — ver §10):** polígonos oficiais do data.rio empacotados, regra
  ligada por default e validada em runtime. `IN_GARAGE` deixou de depender só do
  código de serviço.
- **Endpoints §7.1 não implementados:** `/api/v1/shapes/{id}` e
  `/api/v1/shapes/{id}/corridor` (expor a geometria já resolvida do `gtfs-service`).
  `/api/v1/garages` **saiu** com a fatia D.

**Nada mais está bloqueado por falta de dado.** OUT_OF_ROUTE e §5 vêm do
`sppo-gtfs-service` (§8/§9); a geofence de garagem veio do data.rio (§10).

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
   `*IntegrationTest` — a **CI do GitHub Actions** os executa, e com Docker local
   `mvn --batch-mode verify` também: **executados e verdes localmente** (5 ITs,
   2026-07-25) além da CI.
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

4. **[ENDEREÇADO] Content-Type do feed da SMTR.** No primeiro deploy real descobriu-se
   que a API `dados.mobilidade.rio/gps/sppo` responde **JSON válido rotulado como
   `text/html`** (e, sob carga, devolve página HTML de erro *"Service Unavailable"* com
   HTTP 200). O `WebClient` recusava por content-type → **todo** ciclo falhava (cooldown).
   Corrigido no `DadosMobilidadeRioGpsProvider`: o corpo é lido como **texto** e
   desserializado como JSON independentemente do content-type; página HTML de erro vira
   **falha transitória** (re-tentada). ⚠️ O feed é **instável** por conta da SMTR — quando
   está fora, o mapa fica vazio até a fonte voltar (não é bug do backend).

5. **[ENDEREÇADO] Cache-vazio-preso da resolução de shapes.** Descoberto no deploy real:
   enquanto o `sppo-gtfs-service` estava com índice `unloaded` (credencial ausente), ele
   respondia `404` para **todas** as linhas; o backend cacheava esse "vazio" com o TTL longo
   (6 h) e, mesmo após o gtfs voltar, seguia servindo vazio — só resolvendo após restart.
   Corrigido: `RouteGeometryCache` agora usa um **TTL curto separado para resultados vazios**
   (`gps.route.empty-cache-ttl`, default **5 min**), então um gtfs momentaneamente sem índice
   não congela as linhas. *Companion opcional (outro repo):* o `sppo-gtfs-service` retornar
   `503` (em vez de `404`) enquanto `unloaded`, para o backend tratar como falha transitória
   (não-cacheável) — recomendado, mas o TTL curto já resolve do lado do backend.

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

0. ⏭️ **Geofence de garagem (`GARAGE_GEOFENCE`, §4.3)** — **próxima feature
   escolhida**. Escopo e pré-requisitos na §10 deste documento.
1. ~~**Fechar débito técnico**~~ **Feito** — ver §5 (contexto Spring + ITs
   Testcontainers na CI).
2. ~~**Popular `operators.json`** com dados reais.~~ **Feito** (de-para por
   consórcio, 1º caractere da ordem — ver §5, débito 3).
3. ~~**Deploy** na VM Oracle Cloud (docker compose).~~ **Artefatos prontos:**
   `Dockerfile`, `docker-compose.yml`, `docker-compose.oracle.yml`, `.env.example`
   e o guia **`docs/deploy-oracle-cloud.md`** (inclui os dois firewalls e HTTPS
   opcional via Caddy). **Stack unificado** (backend + `sppo-gtfs-service` + Redis +
   Postgres) provedor-agnóstico: **`docker-compose.stack.yml`** + `.env.stack.example`
   + guia **`docs/deploy-stack.md`** (espera os dois repos clonados lado a lado; segredos
   em `secrets/`, fora do git). Falta só provisionar o host (Oracle bloqueado no signup;
   alternativas avaliadas: Contabo/Hetzner/Fly.io).
4. ~~**Fonte de shapes** para destravar OUT_OF_ROUTE.~~ **Feito (fatia A)** — ver §8;
   ~~**§5 (máquina de desvio)**~~ **feita (fatia B)** — ver §9. Resta a
   **geofence de garagem** (§10).
5. **CI** — `mvn --batch-mode verify` (unitários + ITs Testcontainers) roda em push/PR
   para `main` (`.github/workflows/ci.yml`), com Dependabot semanal (Maven + Actions).
   Falta o passo de **imagem + deploy** (depende do host provisionado).

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
  é descartado e re-tentado (§4.4). Resultado **vazio** expira em TTL curto
  (`GPS_ROUTE_SHAPE_EMPTY_CACHE_TTL`, 5 min) para não congelar a linha por 6 h quando o
  gtfs-service está momentaneamente sem índice.
- **Infra:** `SppoGtfsShapeProvider` (WebClient → `GET /api/v1/lines/{lineCode}/shapes`,
  decodifica encoded polyline; 404/no_shapes ⇒ vazio autoritativo; falha ⇒ retry),
  `EncodedPolyline` (decoder Google precisão 5), `RouteAdherenceConfig` +
  `RouteAdherenceProperties` (`gps.route`) + `SppoGtfsClientProperties` (`gps.gtfs-service`).

**Flag / default:** `ROUTE_SHAPE_SOURCE=disabled` por padrão — nenhum bean HTTP é
criado e a regra não é montada (comportamento idêntico ao atual). Com
`gtfs-service`, aponta-se `SPPO_GTFS_BASE_URL` (e `SPPO_GTFS_API_KEY` se houver).
Métrica: `gps.route.shape.resolve{result=shapes|empty|failure}`.

**Cobertura de geometria, medida no feed real (2026-07-25):** das **362** linhas
distintas em operação, o `sppo-gtfs-service` resolve **349 com shape** — **95,5% da
frota** (2.359 de 2.471 veículos). Nenhuma linha resolve "existe mas sem shape".
Os 13 não resolvidos são: **8 códigos de estado operacional** (`GARAGEM` 46 veículos,
`ESPECIAL` 31, `FORA DE OP` 9, `1 GAR`/`MANUTENCAO`/`TREINO` 4 cada, `RESERVADO` 2,
`OPER.` 1) — agora **filtrados antes de pedir** (§4.4) — e **5 códigos com cara de linha
que não estão no planejamento**: `001`, `1`, `3`, `441`, `856` (10 veículos) e `2802SV`
(1 veículo, onde `2802` existe com 2 shapes: o feed de GPS usa sufixo `SV` que o
`viagem_planejada_dia` não tem; **deixado sem resolver de propósito** — `SV` costuma ser
*variante* de itinerário, e casar com os shapes de `2802` produziria aderência falsa).

**Corrida de arranque: CORRIGIDA.** O `gtfs-service` carregava o índice em
`ApplicationReadyEvent` — exatamente quando o Boot marca readiness como
*accepting traffic* —, e o compose só esperava `service_started`. Resultado: o backend
subia junto e resolvia a frota inteira contra índice vazio pelos ~20 s da carga do
BigQuery, cacheando esses vazios (**285 resoluções vazias no primeiro minuto** contra 3
com shape, medido em 2026-07-25). O TTL curto de vazio curava em 5 min, mas o contador
cumulativo ficava sujo para sempre. Três mudanças fecharam isso:

1. carga passou para **`ApplicationStartedEvent`** (roda *antes* do readiness virar);
2. healthcheck do container aponta para **`/actuator/health/readiness`**, não
   `/actuator/health` (que fica UP assim que o Spring sobe);
3. backend passou a depender de **`service_healthy`** do gtfs, não `service_started`.

Tem teste guardando a inversão (`IndexReloadRunnerTest`), porque voltar o listener para
`ApplicationReadyEvent` reabre a corrida sem quebrar nada visível.

⚠️ Ao ler `gps.route.shape.resolve{result=empty}`, olhe a **taxa**, não o total: o
contador é cumulativo e um único arranque ruim (ou uma janela sem o gtfs no ar) fica
lá para sempre.

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
- ~~Sem endpoint REST de histórico.~~ **Feito:** `GET /api/v1/route-deviations`
  (filtros vehicleId/serviceCode/type/severity/limit) + destaque no mapa (anel
  vermelho para episódio aberto) e lista "Desvios recentes" (WS ao vivo + carga REST).
- ~~Sem métrica de desvios.~~ **Feito:** `gps.route.deviations{type,severity}` (contador
  Micrometer por evento emitido) em `/actuator/prometheus`.
- Observação = posições **mudadas** (o stream publicado já é pós-dedup/mudança); posições
  idênticas repetidas não contam como novos pontos.
- ⚠️ Não exercitado com `shape-source=gtfs-service` em teste hermético (precisa de
  Redis/JPA/WebClient) — validação vem da CI/integração ou do run local.

---

## 10. Fatia D — geofence de garagem (`GARAGE_GEOFENCE`, §4.3)

Regra de garagem por **localização**, não só por código de serviço: pega o ônibus
recolhido que continua transmitindo linha operacional. **Fatias 1–6 entregues e
validadas em runtime contra o feed real** — ver os números no fim desta seção.

### Fonte dos polígonos

**Dataset oficial do data.rio** — "Garagens de operadores do SPPO no Município do Rio
de Janeiro" — empacotado em **`src/main/resources/geofences/garages.geojson`**:
**37 polígonos**, 652 vértices, ~36 KB, com `garage_id`, `garage_name` (operador),
`endereco` e `bairro`. São os **limites reais do lote**.

Caminhos descartados na investigação (2026-07-25), para não se repetir:
- `rj-smtr.br_rj_riodejaneiro_geo.garagem_polygon` (a camada da SMTR no BigQuery) é uma
  **VIEW sobre `rj-smtr-staging`** → `Access Denied`. Nada equivalente em `datario`.
- **Derivar por clusterização** do histórico de GPS (`gps_sppo.tipo_parada = 'garagem'`)
  funciona, mas entrega *convex hulls* generosos e sem nome de operador — pior geofence
  que o lote oficial. A query ficou no repo como **conferência**, não como fonte:
  `docs/sql/derive-garages.sql`.

⚠️ **Lacuna conhecida, medida:** cruzando os 35 aglomerados derivados do GPS com as 37
garagens oficiais, **21 caem dentro de uma garagem oficial e 14 não** — o maior deles com
207 mil observações em 30 dias. Ou são pontos finais/terminais, ou garagens que entraram
depois da camada (que é de 2021). Enquanto isso, esses veículos seguem classificados por
código de serviço. Reavaliar quando houver uma camada mais nova.

### O que foi implementado (fatias 1–4)

| Camada | Peça | Papel |
|---|---|---|
| `domain/geofence` | `GarageArea` | polígono imutável + bbox derivada; `contains()` por **ray casting** com pré-filtro de bbox. Ray casting (não bbox/hull) porque pátio real é côncavo. **Borda conta como dentro** (decisão explícita). |
| `domain/vehicle` | `GarageGeofenceRule` | tag `GARAGE_GEOFENCE`; **neutra** sem polígonos; (0,0) nunca vira garagem |
| `application/port/out` | `GarageGeofencePort` | lista de áreas; dado estático, lido no boot |
| `infrastructure/.../out/geofence` | `GeoJsonGarageGeofenceAdapter` | lê o GeoJSON (`Polygon` e `MultiPolygon`, ordem `[lon, lat]`, só o anel externo). Arquivo ilegível ⇒ **falha na inicialização**; *feature* inválida isolada ⇒ descartada com aviso. Loga `Loaded garage geofences. count=37`. |
| `infrastructure/config` | `GarageGeofenceConfig` + `GarageGeofenceProperties` | wiring condicional; a regra entra no `PositionClassifier` via `ObjectProvider`, como a `OutOfRouteRule` |

**Flag / default:** `GPS_GARAGE_GEOFENCE_ENABLED` (default **true** — o dado é o lote
oficial, e a regra só *acrescenta* precisão ao `IN_GARAGE` que já vinha do código de
serviço). `GPS_GARAGE_GEOFENCE_RESOURCE` aceita `file:/...`, então dá para atualizar as
garagens num deploy montando o arquivo, **sem rebuild**. Com `enabled=false` os beans
existem mas **nenhum polígono é carregado**: a regra fica neutra (classificação idêntica
à de antes da fatia) e `GET /api/v1/garages` devolve coleção vazia — assim a borda REST
e o publisher não precisam tratar bean opcional.

**Testes:** 23 novos (total do repo: **223**, verdes). `GarageAreaTest` cobre dentro,
fora, **borda**, vértice, pré-filtro de bbox, **concavidade em L**, anel aberto e anel
degenerado; `GarageGeofenceRuleTest` cobre a tag independente do código de serviço e a
integração das regras reais (`serviceCode` operacional dentro do pátio ⇒ `IN_GARAGE`,
`insideGarage=true`, `onRoute=false`); `GeoJsonGarageGeofenceAdapterTest` roda contra o
**arquivo real** (37 áreas, ponto conhecido dentro da Braso Lisboa, Copacabana fora,
ordem lon/lat, `MultiPolygon`, *feature* inválida, fail-fast).

### Fatias 5–6 — endpoint, mapa e rótulo na posição

- **`GET /api/v1/garages`** (§7.1) — **GeoJSON `FeatureCollection`** (RFC 7946), não um
  formato próprio: o consumidor natural é mapa (`L.geoJSON`, QGIS) e o padrão fixa a
  ordem `[lon, lat]`, origem clássica de bug em polígono. `properties` traz
  `id`/`name`/`address`/`neighborhood`/`pointCount`. É também o jeito de auditar, em
  produção, exatamente quais polígonos estão valendo.
- **Mapa (`GET /`)** — camada de garagens com toggle "Mostrar garagens" (ligada por
  default) e popup com operador/endereço/bairro. Os polígonos ficam num **pane próprio
  com `zIndex` 350**, abaixo do `overlayPane` (400), para nunca cobrir os marcadores de
  veículo — independentemente da ordem em que os `fetch` terminam.
- **`garageName` no payload da posição** (REST **e** WebSocket, mesmo DTO): preenchido
  **só quando a tag `GARAGE_GEOFENCE` está presente**; `IN_GARAGE` vindo apenas do código
  de serviço não afirma em *qual* pátio o veículo está — e evita varrer polígonos para a
  frota inteira. O popup do veículo mostra a linha "Garagem" quando existe.
- **`GarageLocator`** (domínio) passou a ser a **lógica única** de pertinência, usada pela
  regra e pela borda — mesmo motivo do `RouteAdherenceEvaluator` único: se as duas
  respostas divergirem, aparece veículo "em garagem" sem garagem.

**Validação em runtime (2026-07-25, feed real da SMTR, `mvn spring-boot:run`):**

```
Loaded garage geofences. count=37
polling: SUCCESS, 1773 posições recebidas
snapshot: 1730 veículos → 1583 IN_OPERATION, 135 IN_GARAGE, 8 SUSPICIOUS, 3 OUT_OF_MUNICIPALITY, 1 STALE
```

- **132** posições com a tag `GARAGE_GEOFENCE`, **todas** com `garageName` preenchido.
- **121** delas *não* seriam `IN_GARAGE` pelo código de serviço — é o ganho líquido da
  fatia (~7% da frota); **97** dessas carregam **linha operacional numérica**, exatamente
  o caso alvo (ônibus recolhido ainda transmitindo linha).
- Precedência conferida no dado real: `B10080` com `linha=MANUTENCAO` recebeu
  `GARAGE_GEOFENCE` **e** `SUSPICIOUS_SERVICE_CODE`, e resolveu como `IN_GARAGE` (§4.2).
- Garagens mais cheias no instante: Transportes Paranapuan (35), Viação Redentor;
  Transportes Futuro (20), Viação Pavunense (16), Viação Vila Real (11).

**Testes:** **36 novos** no total da feature (repo: **241**, verdes; `mvn verify` inclui
os 5 ITs Testcontainers). Além dos de domínio/adapter das fatias 1–4:
`GarageControllerTest` (contrato GeoJSON, ordem lon/lat, anel fechado na resposta,
coleção vazia com a geofence off, campos descritivos omitidos quando ausentes),
`GarageServiceTest` (rótulo só com a tag da geofence) e `MapViewControllerTest`
(guarda de regressão do template: toggle, pane e o fetch do GeoJSON).

### Falta

- Expor `garageName` também no histórico de desvio, se fizer sentido operacional.
- Métrica por garagem (`gps.classifications{garage}` ou contador dedicado) — só se houver
  necessidade; hoje o `GET /api/v1/garages` + filtro por status já respondem no mapa.
- **Revisitar os 14 pontos de parada sem cobertura oficial** (a lacuna medida acima):
  decidir se entram como polígonos desenhados à mão ou se ficam de fora.
- Camada oficial mais nova, quando a SMTR publicar (a atual é de 2021).

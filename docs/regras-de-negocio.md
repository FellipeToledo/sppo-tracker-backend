# GPS Tracker SPPO — Especificação de Regras de Negócio

Documento de referência para **reconstruir a aplicação do zero**. Descreve o
*quê* e o *porquê* de cada regra (comportamento, thresholds, precedências),
independente de linguagem/framework. Os valores default vêm da implementação
atual e podem ser ajustados por configuração.

- **Domínio:** ingestão, normalização e classificação em tempo real das posições
  GPS da frota de ônibus (SPPO) do Rio de Janeiro, com distribuição via REST,
  WebSocket e métricas.
- **Natureza:** serviço *always-on* (worker de polling + servidor WebSocket +
  assinante de eventos). Não é serverless.

---

## 1. Fonte de dados — API pública de GPS do SPPO (SMTR)

- **Endpoint:** `GET https://dados.mobilidade.rio/gps/sppo`
- **Autenticação:** nenhuma (API pública, aberta).
- **Filtro obrigatório:** `dataInicial` e `dataFinal`, formato `YYYY-MM-DD HH:MM:SS`.
  - ⚠️ **Fuso:** a janela é interpretada em **horário de Brasília (America/Sao_Paulo)**.
    Deve ser configurável (se vier vazio, testar UTC).
- **Atualização na origem:** a cada ~20 s.
- **Resposta:** array JSON. Cada item (todos os campos chegam como **string**):

| Campo | Significado | Observação |
|---|---|---|
| `ordem` | ID do veículo (carroceria) | formato `XYYZZZ` (X=A–D, YY=empresa, ZZZ=veículo) |
| `latitude`, `longitude` | posição | **decimal com vírgula** (`-22,89206`) |
| `datahora` | horário do fix GPS | **Unix time em ms** (string) |
| `velocidade` | velocidade instantânea | |
| `linha` | serviço operado | `XXX` regular, `SYXXX` variações, `LECDXX` experimental |
| `datahoraenvio` | horário de comunicação à central | Unix time (ms); é o filtro da API |
| `datahoraservidor` | horário de disponibilização no servidor | Unix time (ms) |

- ⚠️ **A API pública NÃO fornece:** sentido/direção, `route_id`, `trip_id`,
  `shape_id`. Regras que dependem disso (ex.: OUT_OF_ROUTE por rota) ficam
  limitadas ou desligadas.
- **Regras de parsing (obrigatórias):**
  - Coordenadas: aceitar vírgula **e** ponto decimal.
  - Timestamps: interpretar como epoch; tolerar segundos **ou** milissegundos
    (heurística: valor < 1e11 → segundos).
  - Registro malformado (sem coordenadas válidas, sem timestamps obrigatórios)
    é **descartado individualmente** — nunca derrubar o lote inteiro.

---

## 2. Modelo de domínio

### 2.1 Posição do veículo (VehiclePosition)
Campos: `vehicleId`, `serviceCode`, `directionCode`, `routeId`, `tripId`,
`shapeId`, `coordinates`, `speed`, `heading`, `positionTimestamp`,
`sentTimestamp`, `serverTimestamp`, `receivedAt`, `source`.

**Invariantes (validação na criação):**
- `vehicleId` obrigatório (não-vazio, trim).
- `coordinates`, `positionTimestamp`, `sentTimestamp`, `receivedAt` obrigatórios.
- `source` obrigatório (ex.: `DADOS_MOBILIDADE_RIO`).
- `speed`, se presente, **não pode ser negativa** (normalizar negativo → nulo).
- `serverTimestamp`, `directionCode`, `routeId`, `tripId`, `shapeId`, `heading`
  são opcionais (nulos permitidos).

### 2.2 Coordenadas (Coordinates)
- `latitude` ∈ [-90, 90]; `longitude` ∈ [-180, 180] (senão, inválidas).
- `isZeroZero()` = latitude e longitude exatamente 0 (usado por INVALID).

---

## 3. Ingestão (hot path)

Fluxo por ciclo de polling:

```
Scheduler → busca janela [now - overlapWindow, now] no provider
  → para cada posição:
      dedup (TTL)  → se duplicada, conta e ignora
      → detecta mudança vs. snapshot atual → se não mudou, ignora
      → classifica (pipeline de regras)
      → salva snapshot atual (TTL)
      → alimenta detecção de desvio de rota
      → publica evento (Pub/Sub → WebSocket)
      → registra métricas
```

### 3.1 Polling
- Executa em intervalo fixo (`fixed-delay`, default **60 s**).
- **Janela com sobreposição:** consulta `[now - overlapWindow, now]` com
  `overlapWindow` (default **90 s**) **maior** que o `fixed-delay`, para reduzir
  perda de posições entre ciclos. A dedup absorve o overlap.
- Timeout de requisição ao provider (default **10 s**); retry curto (default 2
  tentativas, backoff 1 s) **apenas** para falhas transitórias (timeout, HTTP
  5xx, HTTP 429). Erro funcional (ex.: 400) e payload inválido **não** têm retry.
- **Readiness:** o scheduler só faz polling se o provider está "pronto". Para a
  API pública, está sempre pronto (não há credencial). Abstração mantida para
  permitir outras fontes.
- **Cooldown por falhas:** após **N falhas consecutivas** (default **3**), entra
  em cooldown (default **5 min**) sem chamar o provider, marcando o resultado
  como `SKIPPED`/`FAILURE_COOLDOWN`. Sucesso zera o contador.

### 3.2 Deduplicação
- **Chave:** `vehicleId : positionTimestamp : sentTimestamp`.
- Estado em cache com **TTL** (default **10 min**). Se a chave já existe, a
  posição é duplicada (conta métrica e pula).

### 3.3 Detecção de mudança (vs. snapshot atual)
Uma posição só é processada/publicada se **mudou**. Considera-se mudança quando:
1. Não há snapshot anterior (primeira vez), **ou**
2. A candidata é **mais nova** (`positionTimestamp` posterior) **E** difere em
   pelo menos um: coordenadas, velocidade, heading, ou **contexto de rota**
   (`serviceCode`, `directionCode`, `routeId`, `tripId`, `shapeId`).
- Se a candidata **não** é mais nova que o snapshot, é ignorada.

### 3.4 Snapshot atual (camada de tempo real)
- Um snapshot por veículo, renovado a cada heartbeat, com **TTL** (default
  **15 min**, deve ser **> stale-threshold**). Quando o veículo para de
  transmitir, o snapshot expira e sai do "current".

---

## 4. Classificação da posição

Cada posição recebe **um** status final. Um pipeline coleta as *tags* de todas as
regras e decide o status por **ordem de precedência fixa** (não pela ordem das
regras).

### 4.1 Status possíveis
`IN_OPERATION`, `IN_GARAGE`, `OUT_OF_MUNICIPALITY`, `OUT_OF_ROUTE`,
`SUSPICIOUS`, `INVALID`, `STALE`.

### 4.2 Precedência (a primeira que casar vence)
```
INVALID
  → OUT_OF_MUNICIPALITY
    → IN_GARAGE
      → SUSPICIOUS
        → STALE
          → OUT_OF_ROUTE
            → (senão) IN_OPERATION
```
Observação importante: **STALE vence OUT_OF_ROUTE** — uma posição obsoleta não
afirma aderência ao trajeto (mas a tag OUT_OF_ROUTE é preservada no conjunto de
tags).

### 4.3 Regras

| Regra | Tag(s) | Quando dispara | Threshold/dado |
|---|---|---|---|
| **Coordenadas inválidas** | `INVALID_COORDINATES` | posição é exatamente (0, 0) | — |
| **Fora do município** | `OUT_OF_MUNICIPALITY` | fora do bounding box do Rio | box: lat [-23.10, -22.70], lon [-43.80, -43.05] (configurável) |
| **Garagem (geofence)** | `GARAGE_GEOFENCE` | dentro de um polígono de garagem | polígonos GeoJSON empacotados |
| **Código de serviço — garagem** | `IN_GARAGE` | `serviceCode` ∈ conjunto de garagem | `GARAGEM`, `1 GAR` |
| **Código de serviço — suspeito** | `SUSPICIOUS_SERVICE_CODE` | `serviceCode` ∈ conjunto suspeito | `MANUTENCAO`, `MANUT`, `VISTORIA`, `FORA DE OP`, `FORA OP`, `FORA DE OPERACAO`, `TREINO`, `TREINA`, `OPER.`, `00000` |
| **Posição obsoleta (stale)** | `STALE` | `positionTimestamp + threshold < now` | threshold default **5 min** |
| **Fora de rota** | `OUT_OF_ROUTE` | fora do corredor de **todos** os shapes da rota | ver 4.4 |

**Normalização do `serviceCode`** (para as regras de código): remover acentos,
`trim`, colapsar espaços, upper-case. Compara contra os conjuntos acima.

**IN_GARAGE** é atingido por geofence **ou** por código de serviço de garagem.

### 4.4 Regra OUT_OF_ROUTE (resolução de geometria)
- **Não confia no `shapeId` do payload** (vem trocado ida/volta com frequência).
- **Preferencial:** resolve **todos os shapes** da linha pelo `routeId` e testa a
  posição contra o **corredor** (buffer dos segmentos, ex.: ±15 m) de cada um.
  - Está "na rota" se cair no corredor de **qualquer** shape.
  - É `OUT_OF_ROUTE` só se estiver fora de **todos** os shapes resolvidos.
- **Fallback (sem `routeId` ou fonte indisponível):** usa o `shapeId` do payload
  — corredor; sem corredor, distância ao traçado cru comparada ao limiar
  (default **100 m**).
- A resolução de geometria **não roda no hot path**: shapes são resolvidos em
  background (cache assíncrono bounded); sob pico, o excesso é descartado e
  re-tentado, sem bloquear o polling.
- ⚠️ **Com a API pública** (sem `routeId`/`shapeId`), esta regra fica
  efetivamente **desligada** — configurável via fonte de shapes
  (`shape-geom` | `fixture` | `disabled`).

### 4.5 Flags derivadas da classificação
Além do status, a classificação expõe booleans para consumo/filtragem:
`valid`, `insideMunicipality`, `insideGarage`, `onRoute` + o conjunto de `tags`.

| Status | valid | insideMunicipality | insideGarage | onRoute |
|---|---|---|---|---|
| IN_OPERATION | ✔ | ✔ | ✘ | ✔ |
| INVALID | ✘ | ✘ | ✘ | ✘ |
| OUT_OF_MUNICIPALITY | ✔ | ✘ | ✘ | ✔ |
| IN_GARAGE | ✔ | ✔ | ✔ | ✘ |
| SUSPICIOUS | ✔ | ✔ | ✘ | ✘ |
| STALE | ✔ | ✔ | ✘ | ✔ se não houver tag OUT_OF_ROUTE, senão ✘ |
| OUT_OF_ROUTE | ✔ | ✔ | ✘ | ✘ |

---

## 5. Eventos de desvio de itinerário (route deviation)

Ortogonal à classificação. Transforma a sequência de observações
dentro/fora do corredor de cada veículo em **episódios de desvio**, com duas
camadas e histerese temporal (máquina de estados pura por veículo).

### 5.1 Aderência (dentro/fora + distância)
Espelha a decisão do OUT_OF_ROUTE (resolve shapes por rota, testa corredor), mas
**também devolve a distância** (magnitude). Se a geometria ainda não está em
cache, devolve `unresolved` e a detecção espera o próximo ciclo.

### 5.2 "Efetivamente fora"
Um ponto conta como fora **apenas** se: fora do corredor **E** distância >
**margem de confirmação** (default **30 m**). Fora raso = *skimming* de borda,
tratado como dentro (evita flapping).

### 5.3 Máquina de estados (fases: ON_ROUTE / OFF_ROUTE)
- **C1 — ALERT (ao vivo, provisório):** após **N pontos consecutivos** fora
  (default **3**), abre o episódio e emite `ALERT`. Transita para OFF_ROUTE.
- **C2 — CONFIRMED (consolidado):** dentro do episódio, confirma quando
  **sustentado por tempo** (default **3 min**) **OU** distância máxima >
  **distância de confirmação** (default **150 m**). Emitido no máximo uma vez.
- **RETURN:** após **N pontos consecutivos** dentro (default **3**), se já houve
  CONFIRMED, fecha com `RETURN` (voltou ao itinerário) e reseta.
- **CANCELLED:** voltou para dentro antes de consolidar (só houve ALERT) → fecha
  com `CANCELLED` (transitório).
- **Sweep periódico** (default **30 s**): consolida episódios de veículos que
  saíram e **ficaram em silêncio** fora do corredor (sem novas posições), desde
  que o tempo sustentado tenha decorrido.
- **Estado por veículo** em cache com **TTL** (default **6 h**).

### 5.4 Severidade (pela distância máxima do episódio)
- `LEVE` ≤ **150 m** < `MEDIO` ≤ **500 m** < `GRAVE`. (limiares configuráveis)

### 5.5 Publicação
Eventos publicados em tempo real (Pub/Sub → WebSocket `/topic/route-deviations`
[e `/route/{routeId}`]). Histórico persistido (ver §7).

---

## 6. Consórcio do veículo (de-para ordem→consórcio)

- Reference data **estática** (JSON empacotado), carregada uma vez na
  inicialização (fora do hot path).
- Resolve o **consórcio operador** pelo **primeiro caractere da ordem**
  (`vehicleId`, upper-case), que identifica o consórcio — ver §1, formato
  `XYYZZZ` (X=A–D):
  - `A` → Consórcio Intersul
  - `B` → Consórcio Internorte
  - `C` → Consórcio Transcarioca
  - `D` → Consórcio Santa Cruz
- Exposto na API e usado no dashboard para rótulo/filtro por consórcio.
- ℹ️ O feed público dá a **carroceria** (ordem), não o CNPJ da empresa; a
  granularidade confiável e pública é o **consórcio** (1º caractere). Um de-para
  empresa-a-empresa exigiria a relação de frota oficial da SMTR/consórcios.

---

## 7. Distribuição e persistência

### 7.1 REST (v1)
| Método | Caminho | Descrição |
|---|---|---|
| GET | `/api/v1/vehicle-positions/current` | snapshot atual; filtros: `serviceCode`, `routeId`, `classificationStatus` |
| GET | `/api/v1/gps-polling/status` | status do último ciclo de polling |
| GET | `/api/v1/garages` | lista de garagens (geofences) |
| GET | `/api/v1/operators` | de-para de operadoras |
| GET | `/api/v1/shapes/{shapeId}` | geometria do shape (quando fonte disponível) |
| GET | `/api/v1/shapes/{shapeId}/corridor` | corredor (buffer) do shape |

### 7.2 WebSocket / STOMP
- Endpoint: `/ws`.
- Tópicos: `/topic/vehicle-positions` [`/service/{serviceCode}` | `/route/{routeId}`];
  `/topic/route-deviations` [`/route/{routeId}`].

### 7.3 Métricas (Prometheus)
Polling (execuções, duração, falhas consecutivas, últimos contadores
received/valid/duplicated/changed/published/ignored), provider (chamadas
externas, duração, janela solicitada), classificações por status, idade/atrasos
das posições (ingestão, envio, servidor), resolução de shapes. Endpoints
`/actuator/health`, `/actuator/prometheus`.

### 7.4 Persistência
- **Redis:** snapshot atual (TTL), chaves de deduplicação (TTL), estado de desvio
  por veículo (TTL). Também é o barramento **Pub/Sub** para os eventos.
- **Postgres:** histórico de eventos de desvio de itinerário (via migrações
  versionadas / Flyway).

---

## 8. Configuração (env vars e defaults)

| Variável | Default | O que controla |
|---|---|---|
| `GPS_POLLING_ENABLED` | `true` | liga/desliga o polling |
| `GPS_POLLING_FIXED_DELAY` | `60s` | intervalo entre ciclos |
| `GPS_POLLING_OVERLAP_WINDOW` | `90s` | janela de sobreposição (> fixed-delay) |
| `GPS_PROVIDER_REQUEST_TIMEOUT` | `10s` | timeout da chamada ao provider |
| `GPS_DEDUPLICATION_TTL` | `10m` | TTL das chaves de dedup |
| `GPS_POLLING_FAILURE_COOLDOWN_THRESHOLD` | `3` | falhas consecutivas p/ cooldown |
| `GPS_POLLING_FAILURE_COOLDOWN` | `5m` | duração do cooldown |
| `GPS_CURRENT_SNAPSHOT_TTL` | `15m` | TTL do snapshot atual (> stale) |
| `GPS_STALE_POSITION_THRESHOLD` | `5m` | idade p/ marcar STALE |
| `GPS_OUT_OF_ROUTE_DISTANCE_THRESHOLD_METERS` | `100` | fallback de distância ao traçado |
| `ROUTE_SHAPE_SOURCE` | `shape-geom` | fonte de shapes: `shape-geom`\|`fixture`\|`disabled` |
| `RIO_MUNICIPALITY_{MIN,MAX}_{LATITUDE,LONGITUDE}` | box do Rio | bounding box do município |
| `DADOS_RIO_REQUEST_TIME_ZONE` | `America/Sao_Paulo` | fuso do filtro da API |
| `DADOS_RIO_RETRY_MAX_ATTEMPTS` / `_BACKOFF` | `2` / `1s` | retry do provider |
| `GPS_ROUTE_DEVIATION_ENABLED` | `true` | liga eventos de desvio |
| `GPS_ROUTE_DEVIATION_CONFIRMATION_MARGIN_METERS` | `30` | margem p/ "efetivamente fora" |
| `GPS_ROUTE_DEVIATION_ALERT_POINTS` | `3` | pontos consecutivos p/ ALERT (C1) |
| `GPS_ROUTE_DEVIATION_CONFIRM_SUSTAINED` | `3m` | tempo p/ CONFIRMED (C2) |
| `GPS_ROUTE_DEVIATION_CONFIRM_DISTANCE_METERS` | `150` | distância p/ CONFIRMED imediato |
| `GPS_ROUTE_DEVIATION_RETURN_POINTS` | `3` | pontos consecutivos p/ RETURN |
| `GPS_ROUTE_DEVIATION_SWEEP_INTERVAL` | `30s` | intervalo do sweep |
| `GPS_ROUTE_DEVIATION_STATE_TTL` | `6h` | TTL do estado de desvio |
| `GPS_ROUTE_DEVIATION_SEVERITY_MEDIUM_METERS` / `_HIGH_METERS` | `150` / `500` | limiares de severidade |
| `REDIS_HOST` / `REDIS_PORT` | `localhost` / `6379` | Redis |
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | localhost / 5432 / sppotracker / … | Postgres |
| `SERVER_PORT` | `8080` | porta HTTP |

---

## 9. Regras não-funcionais / convenções

- **Arquitetura hexagonal:** domínio puro (sem framework) no centro; casos de uso
  dependem de **portas**, nunca de adapters concretos; infra (REST, WebSocket,
  Redis, Postgres, provider) nas bordas.
- **Extensibilidade da classificação:** nova regra = novo componente que devolve
  tag(s) opcional(is); **a precedência é decidida explicitamente no pipeline**,
  não pela ordem das regras.
- **Erros REST:** padronizados em `application/problem+json`; **nunca** expor
  stack trace nem mensagem interna.
- **Idempotência/robustez do hot path:** um registro ruim nunca derruba o lote;
  resolução de geometria nunca bloqueia o polling.
- **Fusos/tempo:** persistir/computar em UTC; converter só na borda (filtro da
  API em horário de Brasília).
- **Commits:** Conventional Commits, mensagens em inglês.

---

## 10. TODO — infraestrutura e evoluções

- [ ] **Deploy gratuito na VM ARM Ampere da Oracle Cloud (Always Free)** — plano
      que estava pronto para aplicar:
  - VM Ubuntu 22.04, shape `VM.Standard.A1.Flex` (2 OCPU / 12 GB, grátis).
  - Stack via Docker Compose **autossuficiente**: backend + Redis + Postgres
    (build a partir do Dockerfile; **sem segredo**, pois a API de GPS é pública).
  - **Dois firewalls** a liberar (a pegadinha da Oracle): Security List da subnet
    **e** o `iptables` local do Ubuntu (`INPUT` porta da app).
  - `restart: unless-stopped` para sobreviver a reboot; volume persistente do
    Postgres/Redis.
  - Opcionais: HTTPS/domínio via Caddy; Prometheus+Grafana; observabilidade.
  - *(Referência: guia detalhado `deploy-oracle-cloud.md` + `docker-compose.oracle.yml`.)*
- [ ] **OUT_OF_ROUTE com a API pública:** como o feed público não traz
      `routeId`/`shapeId`, avaliar fonte alternativa de itinerário (GTFS/shape-geom
      próprio) ou manter a regra desligada.
- [ ] **Unificar** a lógica dentro/fora do corredor entre `OutOfRouteRule` e o
      resolvedor de aderência do desvio (hoje duplicada de propósito).
- [ ] **Índices por classificação** em Redis, caso o filtro em memória do
      `/current` não escale.
- [ ] **Dashboard/observabilidade** (mapa da frota por status, eventos ao vivo)
      como front separado — candidato natural a deploy na Vercel, consumindo esta API.

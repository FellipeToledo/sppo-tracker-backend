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
- **Snapshot ilegível é pulado, não fatal.** Deploy que muda o formato do payload
  (renomear uma tag, por exemplo) invalida o JSON já gravado, que ainda vive pelo TTL.
  A leitura do "current" **descarta a entrada ilegível com aviso e devolve o resto** —
  um registro ruim não derruba o lote (§9). O veículo reaparece no próximo heartbeat,
  já no formato novo.

---

## 4. Classificação da posição

Cada posição recebe **um** status final. Um pipeline coleta as *tags* de todas as
regras e decide o status por **ordem de precedência fixa** (não pela ordem das
regras).

> **Princípio: não se descarta posição, categoriza-se.** Toda posição que chega
> mapeável recebe status e vai para o snapshot, o REST, o WebSocket e o mapa —
> inclusive quando falta dado para avaliar alguma regra. O único ponto de perda é o
> registro **malformado** do feed (sem coordenada válida, logo sem o que plotar), e
> mesmo esse é **contado** em `gps.provider.records{result=malformed}`. Quando não há
> itinerário para avaliar, a posição é rotulada `ROUTE_NOT_PLANNED` (§4.4) em vez de passar
> como se estivesse na rota.

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
| **Garagem (geofence)** | `GARAGE_GEOFENCE` | dentro de um polígono de garagem | polígonos GeoJSON empacotados (`resources/geofences/garages.geojson`) |
| **Código de serviço — garagem** | `IN_GARAGE` | `serviceCode` ∈ conjunto de garagem | `GARAGEM`, `1 GAR` |
| **Código de serviço — suspeito** | `SUSPICIOUS_SERVICE_CODE` | `serviceCode` ∈ conjunto suspeito | `MANUTENCAO`, `MANUT`, `VISTORIA`, `FORA DE OP`, `FORA OP`, `FORA DE OPERACAO`, `TREINO`, `TREINA`, `OPER.`, `00000` |
| **Posição obsoleta (stale)** | `STALE` | `positionTimestamp + threshold < now` | threshold default **5 min** |
| **Fora de rota** | `OUT_OF_ROUTE` | fora do corredor de **todos** os shapes da rota | ver 4.4 |
| **Sem planejamento** | `ROUTE_NOT_PLANNED` | código **é de linha** e a fonte respondeu que ela **não tem itinerário no feed vigente** | rótulo; **não** define status |

**Normalização do `serviceCode`** (para as regras de código): remover acentos,
`trim`, colapsar espaços, upper-case. Compara contra os conjuntos acima.

**IN_GARAGE** é atingido por geofence **ou** por código de serviço de garagem.

**Origem dos polígonos de garagem:** dataset **oficial do data.rio** ("Garagens de
operadores do SPPO"), empacotado em `resources/geofences/garages.geojson` — 37 polígonos
com operador, endereço e bairro, nos limites reais do lote. O teste é
**ponto-em-polígono** (ray casting), não bounding box: pátio real é côncavo. Borda conta
como dentro. Lacuna medida e ressalvas em `docs/estado-do-projeto.md` §10.

### 4.4 Regra OUT_OF_ROUTE (resolução de geometria)
- **Não confia no `shapeId` do payload** (vem trocado ida/volta com frequência).
- **A largura do corredor é definida pela SMTR: 20 m.** Fonte:
  `rj-smtr.planejamento.segmento_shape`, coluna `buffer_completo` — *"Área de 20 m ao redor
  do segmento"*. É o valor oficial; `GPS_ROUTE_CORRIDOR_METERS` existe para ajuste
  operacional, **não** para inventar largura (ver §9).
- ⚠️ **Os flags de exclusão de segmento NÃO entram nesta regra.**
  `indicador_segmento_desconsiderado`, `indicador_segmento_pequeno` (< 990 m),
  `indicador_tunel` e `indicador_area_prejudicada` pertencem à **validação de viagem**
  (§11), onde a pergunta é *"o veículo cumpriu este segmento?"* — e aí faz sentido
  descartar trecho curto demais para afirmar percurso. Aqui a pergunta é **geométrica**:
  a posição está sobre o itinerário ou não. **Um trecho continua sendo itinerário mesmo
  que a SMTR não o valide como segmento** — logo, o corredor é o **shape inteiro**, sem
  exclusão alguma.
  *Corroborando:* são **1.397** segmentos "pequenos" para **1.383** shapes — praticamente
  um por shape. É a sobra do último trecho, já que a SMTR corta em pedaços de ~1 km
  (comprimento médio **981 m**). Excluí-los do corredor tiraria a ponta final de quase
  toda linha.
- **Preferencial:** resolve **todos os shapes** da linha pelo `serviceCode` e testa a
  posição contra o corredor de 20 m de cada um (traçado completo).
  - Está "na rota" se cair no corredor de **qualquer** shape.
  - É `OUT_OF_ROUTE` só se estiver fora de **todos** os shapes resolvidos.
- **Por que não carregamos a geometria do buffer** (`buffer`/`buffer_completo`), e sim o
  traçado + a largura de 20 m: distância ≤ 20 m ao traçado é, por definição, o buffer
  completo. E medido em 2026-07-25 no feed vigente, a **união** dos buffers tratados e a
  dos completos têm **área idêntica (100,00%)** nos shapes mais segmentados — o tratamento
  remove sobreposição *entre segmentos vizinhos*, não área de rota. Custo comparado (WKT
  médio por shape): shape inteiro **7,9 KB** · buffers **119,0 KB (15×)**, num total de
  160,8 MB e ~4,6 M vértices. ⚠️ **A geometria tratada passa a ser obrigatória se/quando
  entrar a validação por segmento** (§11) — lá a atribuição ponto→segmento depende
  justamente do tratamento.
- **Fallback (sem `routeId` ou fonte indisponível):** usa o `shapeId` do payload
  — corredor; sem corredor, distância ao traçado cru comparada ao limiar
  (default **100 m**).
- A resolução de geometria **não roda no hot path**: shapes são resolvidos em
  background (cache assíncrono bounded); sob pico, o excesso é descartado e
  re-tentado, sem bloquear o polling.
- **Só resolve código que pode ser linha.** O campo `linha` do feed também carrega
  **estado operacional** (`GARAGEM`, `ESPECIAL`, `FORA DE OP`, `1 GAR`, `MANUTENCAO`,
  `TREINO`, `RESERVADO`, `OPER.`, `00000`), que nunca terá shape. Critério sobre a forma
  normalizada: **sem espaço em branco, ao menos um dígito, e diferente de `0`**. Sem
  isso, o serviço de geometria recebe chamada garantidamente inútil a cada expiração do
  TTL de vazio e a métrica de resolução enche de `empty` permanente, escondendo o sinal
  que importa (linha de verdade sem planejamento). É **pré-filtro do cliente**: não
  altera a normalização compartilhada com o `sppo-gtfs-service`.
- **Sem geometria ⇒ rótulo, não silêncio — e os dois casos são separados.** A posição
  continua no mapa e nas APIs, com o status decidido pelas outras regras, mas **marcada
  como não verificada**. Sem isso apareceria como `IN_OPERATION`/`onRoute` — indistinguível
  de um veículo de fato conferido dentro do corredor.
  - `ROUTE_NOT_PLANNED` — a fonte respondeu de forma **autoritativa** que a linha não tem
    itinerário no feed vigente. **Persistente**, e o único rótulo **exposto**: é o número
    que serve para operação.
  - O caso **transitório** (geometria ainda não resolvida: cache frio, resolução em
    background, executor saturado) é **estado interno** — `RouteAdherence.PENDING` — e
    **não** vira tag. Motivo: o rótulo é gravado no snapshot no instante da classificação
    e envelheceria, mostrando "sem itinerário" para veículo cuja geometria já chegou
    (medido: 324 rótulos assim, sendo 190 das 237 linhas já resolvidas). A distinção
    continua existindo no domínio, via `RouteGeometrySource.availabilityFor()`.
  - Código que não é linha (`GARAGEM`, `ESPECIAL`) não recebe rótulo: não há itinerário a
    avaliar. Quem não sabe distinguir (implementação simples da porta) responde
    `PENDING` — **nunca** afirma que a linha está fora do planejamento.
- **Fonte de geometria** (`ROUTE_SHAPE_SOURCE`): `gtfs-service` | `disabled`.
  Com `disabled` (default) a regra nem é montada — é o comportamento com a API
  pública pura, que não traz `routeId`/`shapeId`. Com `gtfs-service`, os shapes
  vêm do **`sppo-gtfs-service`** resolvidos pelo `serviceCode` da linha (§8).

### 4.5 Flags derivadas da classificação
Além do status, a classificação expõe booleans para consumo/filtragem:
`valid`, `insideMunicipality`, `insideGarage`, `onRoute` + o conjunto de `tags`.

⚠️ **`onRoute` é booleano e não sabe dizer "não sei".** Leia junto com as tags: com
`ROUTE_NOT_PLANNED` presente, `onRoute=true` significa apenas *"não foi constatado fora da
rota"*.

⚠️ **A ausência de `ROUTE_NOT_PLANNED` não afirma nada.** Não significa "conferido na rota"
nem "a linha tem itinerário": também não há tag quando a geometria ainda estava resolvendo
(estado interno `PENDING`, §4.4) **nem quando `ROUTE_SHAPE_SOURCE=disabled`**, caso em que a
regra nem é montada e nenhuma posição é avaliada. Sem a tag, a aderência é simplesmente
**desconhecida**.

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

## 6. Operadora do veículo (de-para ordem→consórcio e →empresa)

Reference data **estática** (JSONs empacotados), carregada uma vez na
inicialização (fora do hot path). Resolve **dois níveis** a partir da ordem
(`vehicleId`, upper-case; ver §1, formato `XYYZZZ`):

- **Consórcio** — pelo **primeiro caractere** (`X` = A–D). Cobre **toda** a frota.
  Fonte: `consortiums.json`.
  - `A` → Consórcio Intersul
  - `B` → Consórcio Internorte
  - `C` → Consórcio Transcarioca
  - `D` → Consórcio Santa Cruz
- **Empresa** — pelos **quatro primeiros caracteres** (letra do consórcio + 3
  dígitos, ex.: `A410` → *Real Auto Onibus Ltda*). **Cobertura parcial**: só os
  prefixos presentes no de-para; um veículo cujo prefixo não esteja mapeado
  resolve **apenas** o consórcio (empresa nula). Fonte: `companies.json`.

Ambos os arquivos são objetos JSON `chave → nome`. A posição exposta na API
carrega `consortiumName` e `companyName`; `GET /api/v1/operators` lista as
empresas conhecidas, cada uma anotada com o consórcio (`consortiumCode`,
`consortiumName`). Usado no dashboard para rótulo/filtro por consórcio e empresa.

> ℹ️ O feed público dá a **carroceria** (ordem), não o CNPJ. O consórcio (1º
> caractere) cobre toda a frota; a empresa (prefixo de 4 chars) vem da relação de
> frota da SMTR e é atualizada conforme novos prefixos entrem em operação.

---

## 7. Distribuição e persistência

### 7.1 REST (v1)
| Método | Caminho | Descrição | Status |
|---|---|---|---|
| GET | `/api/v1/vehicle-positions/current` | snapshot atual; filtros: `serviceCode`, `routeId`, `classificationStatus` | implementado |
| GET | `/api/v1/gps-polling/status` | status do último ciclo de polling | implementado |
| GET | `/api/v1/operators` | de-para de operadoras | implementado |
| GET | `/api/v1/route-deviations` | histórico de desvios; filtros: `vehicleId`, `serviceCode`, `type`, `severity`, `limit` | implementado |
| GET | `/api/v1/garages` | geofences de garagem em **GeoJSON** (`FeatureCollection`; `properties`: id, name, address, neighborhood, pointCount) | implementado |
| GET | `/api/v1/shapes/{shapeId}` | geometria do shape (quando fonte disponível) | **planejado** |
| GET | `/api/v1/shapes/{shapeId}/corridor` | corredor (buffer) do shape | **planejado** |

Além da API, `GET /` serve o **mapa de acompanhamento** (view Thymeleaf + Leaflet)
alimentado por WebSocket, com camada de garagens ligável.

O payload de posição (REST e WebSocket usam **o mesmo DTO**) traz `garageName` quando a
posição caiu numa geofence de garagem — `null` fora de garagem e também quando o
`IN_GARAGE` veio apenas do código de serviço, que não diz em qual pátio o veículo está.

### 7.2 WebSocket / STOMP
- Endpoint: `/ws`.
- Tópicos: `/topic/vehicle-positions` [`/service/{serviceCode}` | `/route/{routeId}`];
  `/topic/route-deviations` [`/route/{routeId}`].

### 7.3 Métricas (Prometheus)
Polling (execuções, duração, falhas consecutivas, últimos contadores
received/valid/duplicated/changed/published/ignored), provider (chamadas
externas, duração, janela solicitada), classificações por status, idade/atrasos
das posições (ingestão, envio, servidor), resolução de shapes
(`gps.route.shape.resolve{result}`) e desvios de itinerário
(`gps.route.deviations{type,severity}`). Endpoints
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
| `GPS_GARAGE_GEOFENCE_ENABLED` | `true` | liga a regra `GARAGE_GEOFENCE` (§4.3) |
| `GPS_GARAGE_GEOFENCE_RESOURCE` | `classpath:geofences/garages.geojson` | GeoJSON dos polígonos; aceita `file:/...` para atualizar sem rebuild |
| `GPS_OUT_OF_ROUTE_DISTANCE_THRESHOLD_METERS` | `100` | fallback de distância ao traçado (shape degenerado) |
| `ROUTE_SHAPE_SOURCE` | `disabled` | fonte de shapes: `gtfs-service`\|`disabled` (liga a regra OUT_OF_ROUTE) |
| `GPS_ROUTE_CORRIDOR_METERS` | `20` | meia-largura do corredor — **valor oficial da SMTR** (`segmento_shape.buffer_completo`, §4.4); alterar só com justificativa |
| `GPS_ROUTE_SHAPE_CACHE_TTL` | `6h` | validade da geometria em cache antes de re-resolver |
| `GPS_ROUTE_SHAPE_EMPTY_CACHE_TTL` | `5m` | validade curta de um resultado **vazio** (evita congelar a linha se o gtfs-service estiver sem índice) |
| `GPS_ROUTE_SHAPE_EMPTY_CACHE_TTL` | `5m` | TTL curto p/ resultado vazio (gtfs sem índice não congela linhas) |
| `GPS_ROUTE_REFRESH_POOL_SIZE` / `_QUEUE_CAPACITY` | `2` / `500` | executor bounded de resolução em background |
| `SPPO_GTFS_BASE_URL` | `http://localhost:8081` | URL do `sppo-gtfs-service` |
| `SPPO_GTFS_API_KEY` | *(vazio)* | `X-Api-Key` p/ o gtfs-service (vazio = sem cabeçalho) |
| `SPPO_GTFS_REQUEST_TIMEOUT` | `5s` | timeout da chamada ao gtfs-service |
| `RIO_MUNICIPALITY_{MIN,MAX}_{LATITUDE,LONGITUDE}` | box do Rio | bounding box do município |
| `DADOS_RIO_BASE_URL` / `DADOS_RIO_PATH` | `https://dados.mobilidade.rio` / `/gps/sppo` | endpoint do provider |
| `DADOS_RIO_REQUEST_TIME_ZONE` | `America/Sao_Paulo` | fuso do filtro da API |
| `DADOS_RIO_RETRY_MAX_ATTEMPTS` / `_BACKOFF` | `2` / `1s` | retry do provider |
| `DADOS_RIO_MAX_IN_MEMORY_SIZE` | `33554432` (32 MB) | teto do corpo da resposta do provider |
| `REDIS_TIMEOUT` | `5s` | timeout das operações Redis |
| `GPS_ROUTE_DEVIATION_ENABLED` | `true` | liga eventos de desvio |
| `GPS_ROUTE_DEVIATION_CONFIRMATION_MARGIN_METERS` | `30` | margem p/ "efetivamente fora" |
| `GPS_ROUTE_DEVIATION_ALERT_POINTS` | `3` | pontos consecutivos p/ ALERT (C1) |
| `GPS_ROUTE_DEVIATION_CONFIRM_SUSTAINED` | `3m` | tempo p/ CONFIRMED (C2) |
| `GPS_ROUTE_DEVIATION_CONFIRM_DISTANCE_METERS` | `150` | distância p/ CONFIRMED imediato |
| `GPS_ROUTE_DEVIATION_RETURN_POINTS` | `3` | pontos consecutivos p/ RETURN |
| `GPS_ROUTE_DEVIATION_SWEEP_INTERVAL` | `30s` | intervalo do sweep |
| `GPS_ROUTE_DEVIATION_STATE_TTL` | `6h` | TTL do estado de desvio |
| `GPS_ROUTE_DEVIATION_SEVERITY_MEDIO_METERS` / `_GRAVE_METERS` | `150` / `500` | limiares de severidade (LEVE/MEDIO/GRAVE) |
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
- **Nenhum limiar inventado.** Todo valor de regra tem de ter **origem rastreável no dado
  oficial** (tabela/coluna do `rj-smtr.*`, com a descrição citada) ou vir de decisão
  registrada aqui. Se não há dado nem definição publicada, a regra fica **desligada** ou a
  posição é marcada como **não avaliada** (ex.: `ROUTE_NOT_PLANNED`, §4.4) — nunca se
  escolhe um número plausível. O sistema mostra a operação do SPPO como o dado diz;
  achismo com aparência de fato é o pior defeito possível aqui.
  *Precedente:* o corredor nasceu como "±15 m" sem origem, quando a SMTR define **20 m**
  em `segmento_shape.buffer_completo` — 25% mais estreito, ignorando túnel e os 3.404
  segmentos que ela manda desconsiderar.
- **Commits:** Conventional Commits, mensagens em inglês.

---

## 10. TODO — infraestrutura e evoluções

- [x] **Deploy gratuito na VM ARM Ampere da Oracle Cloud (Always Free)** —
      artefatos prontos no repo: `Dockerfile`, `docker-compose.yml`,
      `docker-compose.oracle.yml` (overlay de produção), `.env.example` e o guia
      passo a passo **`docs/deploy-oracle-cloud.md`**. Falta apenas provisionar a
      VM e rodar. Resumo do que o guia cobre:
  - VM Ubuntu 22.04, shape `VM.Standard.A1.Flex` (2 OCPU / 12 GB, grátis).
  - Stack via Docker Compose **autossuficiente**: backend + Redis + Postgres
    (build a partir do Dockerfile; **sem segredo**, pois a API de GPS é pública).
  - **Dois firewalls** a liberar (a pegadinha da Oracle): Security List da subnet
    **e** o `iptables` local do Ubuntu (`INPUT` porta da app).
  - `restart: unless-stopped` para sobreviver a reboot; volume persistente do
    Postgres/Redis.
  - Opcionais: HTTPS/domínio via Caddy; Prometheus+Grafana; observabilidade.
  - *(Referência: guia detalhado `deploy-oracle-cloud.md` + `docker-compose.oracle.yml`.)*
- [x] **OUT_OF_ROUTE com a API pública** — resolvido com o **`sppo-gtfs-service`**
      (repo irmão, shapes do GTFS da SMTR via BigQuery), resolvendo por
      `serviceCode`. Ligado por `ROUTE_SHAPE_SOURCE=gtfs-service`; `disabled` é o
      default. Destravou também a máquina de desvio (§5).
- [x] **Unificar** a lógica dentro/fora do corredor — hoje única no
      `RouteAdherenceEvaluator` (domínio), consumido pela `OutOfRouteRule` (§4.4) e
      pelo `RouteDeviationService` (§5).
- [ ] **Geofence de garagem (`GARAGE_GEOFENCE`, §4.3)** — *próxima feature*. Falta
      a fonte dos polígonos GeoJSON de garagem (empacotados no jar), o teste
      ponto-em-polígono no domínio e o endpoint `GET /api/v1/garages` (§7.1). A tag
      já existe; hoje `IN_GARAGE` só é atingido por código de serviço.
- [ ] **Revalidação por ETag** do cache de shapes: o `sppo-gtfs-service` já oferece
      `ETag`/`304` + `feedVersion.id`, mas o cache do backend expira só por TTL (6h).
- [ ] **Índices por classificação** em Redis, caso o filtro em memória do
      `/current` não escale.
- [ ] **Dashboard/observabilidade** — o mapa da frota por status + eventos ao vivo
      já existe embutido em `GET /` (Thymeleaf + Leaflet + WebSocket). Um front
      separado (ex.: Vercel) consumindo esta API segue como evolução opcional.

---

## 11. Validação de viagem por segmento — a SMTR já publica (consumir, não recalcular)

Esta seção planejava **implementar** a contagem de quantos segmentos da viagem o veículo
cumpriu. Em **2026-07-26**, procurando a definição na fonte (§9), descobriu-se que a SMTR
**já publica essa validação pronta**, em `rj-smtr.monitoramento.viagem_validacao`. A fatia
deixa de ser "calcular" e passa a ser "consumir" — e isso **não é preferência de
implementação, é a regra da §9**: paridade com o número que a SMTR usa vale mais que
cálculo próprio equivalente.

### 11.1 A fonte oficial: `rj-smtr.monitoramento.viagem_validacao`

Particionada por `data`. Cada linha é **uma viagem**, já validada, com os campos que a
§11 pretendia produzir:

| coluna | descrição (da própria tabela) |
|---|---|
| `quantidade_segmentos_considerados` | segmentos considerados na validação (já **com** as exclusões aplicadas) |
| `quantidade_segmentos_validos` | segmentos com **pelo menos um sinal de GPS** — a definição oficial de "cumprido" |
| `quantidade_segmentos_necessarios` | **mínimo** de segmentos para a viagem ser validada |
| `indice_validacao` | razão entre válidos e considerados |
| `indicador_trajeto_valido` | `validos >= necessarios` — o veredito de trajeto |
| `indicador_primeiro_segmento_valido` / `..._ultimo_...` | primeiro/último segmento considerado e com GPS |
| `datetime_partida_automatica` / `..._chegada_...` | primeiro/último GPS fora da cerca eletrônica **e dentro do buffer de um segmento** |
| `indicador_viagem_valida` | veredito final da viagem |
| `indicador_servico_divergente`, `indicador_shape_invalido`, `indicador_viagem_sobreposta`, `indicador_prazo_envio`, `indicador_processamento_*` | conformidade regulatória (os `processamento_*` citam **Art. 8º, I e II**) |

Traz ainda `servico`, `consorcio`, `sentido`, `shape_id`, `trip_id`, `route_id`,
`id_veiculo`, `distancia_planejada`, `velocidade_media`, `tipo_dia`, `feed_version`.

**Medido em 2026-07-26** (dry-run antes; ≈ US$ 0,0002 no total):

```
modo = 'Ônibus SPPO'   1.700.973 viagens, de 2026-05-27 a 2026-07-23
latência               D-3 (última partição 07-23; atualizada em 07-26 08:05)
dia 2026-07-23         42.526 viagens · 428 serviços · 3.599 veículos
                       42.006 trajeto válido (98,8%) · 41.478 viagem válida (97,5%)
                       índice de validação médio 0,9927
                       considerados 21,3 · válidos 21,1 · necessários 19,07 (médias)
```

⚠️ `modo` é **`'Ônibus SPPO'`**, não `'Ônibus'` (o filtro errado devolve zero linhas em
silêncio — foi o primeiro resultado desta investigação). Há também `'BRT'`,
`'Ônibus Executivo'` e **330 mil linhas com `modo` nulo** no período.

⚠️ **Dado retrospectivo, D-3.** Não substitui nada do hot path: `OUT_OF_ROUTE` (§4.4) e os
eventos de desvio (§5) seguem em tempo real, a partir da geometria. Esta seção é análise de
conformidade *depois* da viagem.

### 11.2 O limiar não é derivável — tem de ser lido

`quantidade_segmentos_necessarios` **não sai de fórmula simples**. Testado no dia
2026-07-23 (42.526 viagens), percentual de viagens em que cada candidata acerta:

```
floor(0,9 × considerados)   64,4%   <- a melhor, e ainda erra 1 em 3
ceil (0,9 × considerados)   45,4%
considerados - 2            38,8%
considerados - 1            26,6%
ceil (0,8 × considerados)    8,3%
round(0,8 × considerados)    6,4%
floor(0,8 × considerados)    3,0%
necessarios >= considerados   0,0%   <- nunca exige todos os segmentos
```

A razão `necessarios/considerados` varia de **0,50 a 0,93** (média 0,89), e
`considerados` vai de 2 a 75. Portanto: **ler o campo, nunca reproduzir a regra.**
Inventar aqui um "90%" seria repetir exatamente o erro do corredor de ±15 m (§9).

### 11.3 O que isso cancela

A conclusão anterior — carregar o `buffer` tratado e atribuir ponto→segmento por conta
própria — **fica sem motivo**, e era caro:

```
shape inteiro          7,9 KB
linhas segmentadas    13,1 KB   (1,65x)
buffers tratados     119,0 KB   (15x)  · p95 214,3 KB · máx 323,8 KB
total dos buffers    160,8 MB  (4,54 M vértices)   <- não precisamos carregar nada disso
```

(Re-medido em 2026-07-26 no feed vigente — 229 MiB varridos, ≈ US$ 0,0011 — e reproduz
exatamente a medição de 07-25: 119,0 KB/shape e 160,8 MB.)

Além do custo, a atribuição própria **divergiria** do número oficial: "cumprido" na SMTR é
*ter pelo menos um sinal de GPS no buffer do segmento*, contado sobre os segmentos que ela
considera, com um limiar que não conseguimos derivar (11.2).

### 11.4 O que `segmento_shape` continua sendo

`rj-smtr.planejamento.segmento_shape` segue sendo a **origem da largura de 20 m** do
corredor da §4.4 (`buffer_completo` = *"Área de 20m ao redor do segmento"*) — é só isso
que consumimos dela. Feed vigente medido em **2026-07-26**:

| | feed `2026-07-19` (vigente) | feed `2026-07-05` (anterior) |
|---|---|---|
| shapes | 1.383 | 1.412 |
| segmentos | 37.692 (27,3/shape) | 38.468 |
| `indicador_tunel` | 1.786 | 1.868 |
| `indicador_segmento_desconsiderado` | 3.286 | 3.404 |
| `indicador_segmento_pequeno` | 1.368 | 1.397 |
| `indicador_area_prejudicada` | 388 | 398 |
| comprimento médio | 982 m | 981 m |

⚠️ Correção: os números antes registrados aqui como *"feed de 2026-07-24"* são do feed
**`feed_start_date = 2026-07-05`**. `2026-07-24` era o `feed_version` (timestamp de
publicação) daquele feed. Partição e versão são coisas diferentes — o vigente é o de
`feed_start_date` máximo com `feed_end_date` nulo.

⚠️ Detalhe não registrado antes: `indicador_tunel` tem **vigência**
(`inicio_vigencia_tunel` / `fim_vigencia_tunel`). No feed vigente, todos os 1.786
segmentos de túnel têm início preenchido e **nenhum** tem fim — a vigência é aberta. Quem
usar o flag por data de viagem precisa respeitar a janela, não só o booleano.

Os flags de exclusão continuam **não** valendo para o corredor da §4.4 (lá o corredor é o
shape inteiro a 20 m) — e agora também não valem para nós na validação, porque quem os
aplica é a SMTR, dentro de `quantidade_segmentos_considerados`.

### 11.5 Estado do groundwork no `sppo-gtfs-service`

⚠️ **Correção de status:** a branch `feat/segment-exclusions` **está mergeada em `main`**
(PR #3, commit `353c41f`, 2026-07-25 17:28) — o texto anterior desta seção dizia que
ficara fora de propósito, e foi escrito 3 minutos antes do merge. Hoje `EncodedShapeDto`
serializa `segments` **sem toggle**, ou seja: o payload de **1,65×** por shape está valendo
em produção, e com o 11.1 ele deixa de ter consumidor previsto.

**A decidir** (não decidido nesta sessão): tirar `segments` do payload default — por
parâmetro (`?segments=true`) ou revertendo — ou mantê-lo para inspeção. E, para consumir o
11.1: onde encaixar a leitura da `viagem_validacao`, já que o backend hoje não fala com o
BigQuery (quem fala é o `sppo-gtfs-service`, e o contrato dele é *geometry only*).

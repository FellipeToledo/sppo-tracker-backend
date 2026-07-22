# Kickoff — mensagem inicial para o agente (Antigravity)

Cole o bloco abaixo no agente da Antigravity para iniciar o projeto.

```text
Você vai construir o backend "sppo-tracker-backend" (empresa FAJTECH): um serviço
em tempo real que ingere, classifica e distribui as posições GPS da frota de
ônibus (SPPO) do Rio de Janeiro, alimentando um dashboard ao vivo (frontend
separado). É um serviço always-on (worker de polling + WebSocket), NÃO serverless.

Identidade do projeto:
- groupId: com.fajtech
- artifactId / repo: sppo-tracker-backend
- pacote base: com.fajtech.sppotracker
- classe principal: SppoTrackerBackendApplication

## Ponto de partida
Já existe um esqueleto neste repositório (Java 21 / Spring Boot 3.5, arquitetura
hexagonal), na branch `main`. ANTES DE QUALQUER COISA, leia, nesta ordem:
  1) CLAUDE.md                          — arquitetura, comandos, convenções
  2) docs/regras-de-negocio.md          — a ESPECIFICAÇÃO FUNCIONAL COMPLETA (fonte da verdade)
  3) docs/claude-code-best-practices.md — como trabalhar neste projeto
Depois rode `mvn --batch-mode test` para confirmar que o build baixa as
dependências e o teste-semente (CoordinatesTest) passa.

## Fonte de dados (sem credencial)
API pública da SMTR: GET https://dados.mobilidade.rio/gps/sppo
  ?dataInicial=YYYY-MM-DD HH:MM:SS&dataFinal=YYYY-MM-DD HH:MM:SS
- Filtro em horário de Brasília (America/Sao_Paulo).
- Resposta: array JSON; campos são strings; coordenadas com VÍRGULA decimal
  (-22,89206); timestamps em Unix time (ms). NÃO traz routeId/shapeId/sentido.
- Parsing defensivo: aceitar vírgula e ponto; epoch em s ou ms; descartar
  registro malformado individualmente (nunca derrubar o lote).

## Regras de arquitetura (invioláveis)
- Hexagonal: domain/ é puro (sem Spring); application/ depende de PORTAS
  (port/in, port/out), nunca de adapters; infrastructure/ implementa as portas.
- Value objects imutáveis e auto-validados (siga o padrão de Coordinates).
- Erros REST em application/problem+json; nunca expor stack trace.
- Tempo em UTC internamente; converter só na borda.

## Como trabalhar (definition of done)
- Para cada tarefa não-trivial: PLANEJE antes de codar (plan mode).
- TDD: escreva o teste que falha primeiro, depois implemente.
- Commits pequenos, Conventional Commits em inglês.
- "Pronto" = `mvn test` verde + CI verde. Rode /code-review antes de PR.
- Ao mudar convenção, atualize CLAUDE.md/docs no mesmo PR.

## Roadmap de implementação (ordem de dependência)
Implemente em fatias verticais, testando cada uma:
  1. Modelo de domínio: VehiclePosition (+invariantes), VehiclePositionStatus.
  2. Provider (adapter/out): cliente HTTP da API pública + desserializadores
     (vírgula decimal, epoch) + porta FetchExternalGpsPositionsPort.
  3. Ingestão (hot path): scheduler de polling com janela de sobreposição
     (fixed-delay 60s / overlap 90s), cooldown após 3 falhas, readiness.
  4. Deduplicação (Redis, TTL 10m, chave vehicleId:positionTs:sentTs) +
     detecção de mudança (mais novo E coords/velocidade/heading/rota mudou).
  5. Snapshot atual por veículo (Redis, TTL 15m).
  6. Pipeline de classificação + regras, respeitando a PRECEDÊNCIA:
     INVALID > OUT_OF_MUNICIPALITY > IN_GARAGE > SUSPICIOUS > STALE >
     OUT_OF_ROUTE > IN_OPERATION. (thresholds e listas em regras-de-negocio §4)
  7. REST: GET /api/v1/vehicle-positions/current (filtros serviceCode/routeId/
     classificationStatus) e /api/v1/gps-polling/status.
  8. WebSocket/STOMP: /ws + /topic/vehicle-positions[/service|/route].
  9. Métricas Micrometer/Prometheus (polling, provider, classificações).
 10. Persistência Postgres + Flyway (histórico) e eventos de desvio de rota
     (máquina de estados C1/C2 — regras-de-negocio §5). Operadoras (§6).

## Deploy (TODO, depois do MVP)
VM ARM Ampere da Oracle Cloud (Always Free) com docker compose (backend + Redis
+ Postgres). Sem segredo (API pública). Atenção aos DOIS firewalls da Oracle
(Security List da subnet E iptables local do Ubuntu). Detalhes em
docs/regras-de-negocio.md §10.

Comece confirmando o build (mvn test) e me proponha um plano para a fatia 1+2
(domínio + provider) antes de escrever código.
```

# GPS Tracker Backend (SPPO Rio)

Backend Java 21 / Spring Boot que ingere, normaliza, deduplica, classifica e
distribui em tempo real as posições GPS da frota de ônibus (SPPO) do Rio de
Janeiro, a partir da **API pública da SMTR** (`dados.mobilidade.rio/gps/sppo`).

Monólito modular com **arquitetura hexagonal**. Sem credencial de provider.

## Documentação

- **`docs/rodar-local.md`** — **como subir com Docker na sua máquina** e conferir que
  está funcionando (inclui como validar o `OUT_OF_ROUTE`). Comece por aqui.
- **`docs/regras-de-negocio.md`** — especificação funcional completa (a fonte da
  verdade do domínio: ingestão, classificação, desvio de rota, thresholds).
- **`docs/estado-do-projeto.md`** — **onde o projeto parou**: o que está pronto, o
  que foi adiado, débito técnico e a próxima feature. Leia ao retomar o trabalho.
- **`CLAUDE.md`** — guia curto de arquitetura, comandos e convenções para o agente.
- **`docs/claude-code-best-practices.md`** — como aproveitar o Claude Code ao
  máximo neste projeto.
- Deploy: **`docs/deploy-stack.md`** (stack unificado com o `sppo-gtfs-service`) e
  **`docs/deploy-oracle-cloud.md`** (VM Always Free, passo a passo).

## Serviço irmão

A geometria dos itinerários (shapes do GTFS da SMTR) vem do
**[`sppo-gtfs-service`](https://github.com/FellipeToledo/sppo-gtfs-service)** — repo
separado, lido via HTTP e usado para `OUT_OF_ROUTE` (§4.4) e para os eventos de
desvio (§5). Desligado por default (`ROUTE_SHAPE_SOURCE=disabled`).

## Stack

Java 21 · Spring Boot 4.1 · Spring Web / WebFlux · Data Redis · WebSocket/STOMP ·
Data JPA + Flyway (Postgres) · Actuator · Micrometer/Prometheus · JUnit 5.

## Rodando

Guia completo (os dois modos, verificação e problemas comuns):
**[`docs/rodar-local.md`](docs/rodar-local.md)**.

```bash
# backend + Redis + Postgres, sem segredo (OUT_OF_ROUTE desligado):
docker compose up -d --build
curl -s http://localhost:8090/actuator/health

# com geometria — soma o sppo-gtfs-service e liga OUT_OF_ROUTE + desvios:
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build

# ou local (precisa de Redis:6379 e Postgres:5432):
mvn spring-boot:run
```

## Testes

```bash
mvn --batch-mode test
```

## Estrutura

```
src/main/java/com/fajtech/sppotracker
├── domain          # regras e modelos puros (sem framework)
├── application     # casos de uso + portas (in/out)
└── infrastructure  # adapters (rest, scheduler, websocket, redis, provider), config
```

## Deploy

Serviço *always-on* (worker + WebSocket). Alvo recomendado: **VM ARM Ampere da
Oracle Cloud (Always Free)** com `docker compose` — stack autossuficiente e sem
segredo. Guia passo a passo (incl. os dois firewalls e HTTPS opcional):
**[`docs/deploy-oracle-cloud.md`](docs/deploy-oracle-cloud.md)**.

```bash
cp .env.example .env   # troque DB_PASSWORD
docker compose -f docker-compose.yml -f docker-compose.oracle.yml up -d --build
```

Para subir **os dois serviços juntos** (backend + `sppo-gtfs-service` + Redis +
Postgres), em qualquer host com Docker:
**[`docs/deploy-stack.md`](docs/deploy-stack.md)**.

```bash
cp .env.stack.example .env.stack   # DB_PASSWORD, GTFS_API_KEY, GCP_PROJECT_ID…
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
```

## Convenção de commits

Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).

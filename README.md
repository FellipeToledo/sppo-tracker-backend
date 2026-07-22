# GPS Tracker Backend (SPPO Rio)

Backend Java 21 / Spring Boot que ingere, normaliza, deduplica, classifica e
distribui em tempo real as posições GPS da frota de ônibus (SPPO) do Rio de
Janeiro, a partir da **API pública da SMTR** (`dados.mobilidade.rio/gps/sppo`).

Monólito modular com **arquitetura hexagonal**. Sem credencial de provider.

## Documentação

- **`docs/regras-de-negocio.md`** — especificação funcional completa (a fonte da
  verdade do domínio: ingestão, classificação, desvio de rota, thresholds).
- **`docs/claude-code-best-practices.md`** — como aproveitar o Claude Code ao
  máximo neste projeto.
- **`CLAUDE.md`** — guia curto de arquitetura, comandos e convenções para o agente.

## Stack

Java 21 · Spring Boot 4.1 · Spring Web / WebFlux · Data Redis · WebSocket/STOMP ·
Data JPA + Flyway (Postgres) · Actuator · Micrometer/Prometheus · JUnit 5.

## Rodando

```bash
# stack completa (backend + Redis + Postgres), sem segredo:
docker compose up -d --build
curl -s http://localhost:8090/actuator/health

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
Oracle Cloud (Always Free)** com `docker compose`. Ver TODO em
`docs/regras-de-negocio.md` §10.

## Convenção de commits

Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).

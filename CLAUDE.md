# CLAUDE.md

Guia para o Claude Code (e qualquer agente) trabalhar neste repositório. Mantenha
este arquivo atualizado no mesmo PR sempre que uma convenção mudar.

## Visão geral

Backend Java 21 / Spring Boot 3.5 que faz polling da **API pública de GPS do SPPO**
(SMTR — `dados.mobilidade.rio/gps/sppo`), normaliza/deduplica/classifica posições
GPS de ônibus e as distribui em tempo real via REST, WebSocket/STOMP e métricas
Prometheus. **Monólito modular com arquitetura hexagonal.**

> A especificação funcional completa (regras, thresholds, precedências) está em
> **`docs/regras-de-negocio.md`** — é a fonte da verdade do domínio. Leia antes de
> implementar regras.
>
> **Onde o projeto parou** (o que está pronto, o que foi adiado e o débito técnico):
> **`docs/estado-do-projeto.md`** — leia ao iniciar uma nova sessão.

## Comandos

```bash
mvn --batch-mode test                 # roda todos os testes (JUnit 5 + AssertJ)
mvn -Dtest=CoordinatesTest test        # roda uma classe
mvn -Dtest=CoordinatesTest#shouldDetectZeroZero test   # um método
mvn spring-boot:run                   # sobe local (precisa de Redis:6379 e Postgres:5432)
docker compose up -d --build          # sobe stack completa (backend + Redis + Postgres)
```

## Arquitetura

Pacote raiz `com.fajtech.sppotracker`. Três camadas, dependências sempre
apontando para dentro (ver `package-info.java` de cada uma):

- `domain/` — modelos e regras puros, sem Spring. Value objects imutáveis
  (records) auto-validados. **Ex.: `Coordinates` é o padrão a seguir.**
- `application/` — casos de uso + **portas** (`port/in`, `port/out`). Serviços
  dependem de portas, nunca de adapters concretos.
- `infrastructure/` — adapters de entrada (`adapter/in`: rest, scheduler,
  websocket) e saída (`adapter/out`: provider HTTP, redis, persistence), `config/`
  (`@ConfigurationProperties`) e `observability/`.

## Domínio em poucas linhas (detalhes em docs/regras-de-negocio.md)

- **Ingestão (hot path):** polling com janela de sobreposição → dedup (TTL) →
  detecção de mudança → classificação → snapshot → publica evento → métricas.
- **Classificação:** um status por posição, decidido por **precedência fixa** no
  pipeline: `INVALID → OUT_OF_MUNICIPALITY → IN_GARAGE → SUSPICIOUS → STALE →
  OUT_OF_ROUTE → IN_OPERATION`. Ao adicionar regra: implemente-a, **atualize
  explicitamente a precedência no pipeline** e os flags da classificação.
- **Provider:** API pública, **sem credencial**. Coordenadas com vírgula decimal
  e timestamps em Unix time (desserializar defensivamente). O feed **não** traz
  `routeId`/`shapeId`/sentido.

## Convenções

- **Commits:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`,
  `docs:`, `chore:`), mensagens em inglês.
- **Erros REST:** `application/problem+json`; nunca expor stack trace.
- **Testes** acompanham a classe testada no mesmo pacote (`*Test.java`).
- **Tempo** em UTC internamente; converter só na borda (filtro da API em BRT).

## Como trabalhar aqui (fluxo esperado)

1. Tarefa não-trivial → **plan mode** primeiro.
2. Regra nova → ler `docs/regras-de-negocio.md`, **escrever o teste que falha**,
   implementar, atualizar precedência/flags, `mvn test`.
3. Antes de abrir PR → `/code-review` (e `/security-review` quando tocar em I/O).
4. Manter `CLAUDE.md` e `docs/` atualizados quando convenções mudarem.

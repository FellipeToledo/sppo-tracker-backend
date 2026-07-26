# Rodar localmente com Docker

Guia curto para subir o projeto na máquina de desenvolvimento e conferir que está
funcionando. Para hospedar num servidor, ver **`docs/deploy-stack.md`** (é ele que
detalha variáveis, limites de memória, firewall e troubleshooting — aqui não repetimos).

## Dois modos

| | `docker-compose.yml` | `docker-compose.stack.yml` |
|---|---|---|
| Sobe | backend + Redis + Postgres | + `sppo-gtfs-service` |
| Precisa de credencial GCP | não | sim (ou perfil `demo`) |
| `OUT_OF_ROUTE` e desvios (§5) | **desligados** | **ligados** |
| Use quando | mexer em ingestão, classificação, mapa, API | mexer em aderência de rota / desvio |

O GPS vem sempre da **API pública da SMTR**, sem credencial. O BigQuery só entra para a
**geometria dos itinerários**, servida pelo `sppo-gtfs-service`.

## Pré-requisitos

- **Docker rodando.** No Windows, o Docker Desktop precisa estar aberto — se estiver
  fechado, qualquer comando falha com `failed to connect to the docker API at
  npipe:////./pipe/dockerDesktopLinuxEngine`. Abra o Docker Desktop e espere o ícone ficar
  verde (`docker version` deve responder).
- Para o modo stack, os **dois repos clonados lado a lado**:

  ```
  bus-gps-analitcs/
    sppo-tracker-backend/   # rode o compose daqui
    sppo-gtfs-service/      # build context ../sppo-gtfs-service
  ```

## Modo simples (sem geometria)

```bash
cd sppo-tracker-backend
cp .env.example .env          # troque DB_PASSWORD
docker compose up -d --build
curl -s http://localhost:8090/actuator/health
```

Mapa em <http://localhost:8090/>. `OUT_OF_ROUTE` não aparece neste modo — o classificador
não tem geometria, então nenhuma posição é testada contra o corredor.

## Modo stack (com fora de rota e desvios)

```bash
cd sppo-tracker-backend
cp .env.stack.example .env.stack     # DB_PASSWORD, GTFS_API_KEY, GTFS_ADMIN_KEY, GCP_PROJECT_ID

# credencial do BigQuery, FORA da árvore do repo
mkdir -p ~/.secrets/sppo-gtfs
cp /caminho/da/sua-sa.json ~/.secrets/sppo-gtfs/gcp-sa.json
# e no .env.stack:  GCP_SECRETS_DIR=C:/Users/SEU_USUARIO/.secrets/sppo-gtfs
#                   (caminho absoluto; no Windows use barras normais)

docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
```

O arquivo **precisa** se chamar `gcp-sa.json` — o compose monta o diretório read-only em
`/secrets` e o container lê `/secrets/gcp-sa.json`. Sem credencial nenhuma, use
`GTFS_PROFILE=demo` no `.env.stack` (amostra em memória, geometria fictícia — serve para
ver a tela subir, não para conferir número).

### O que esperar (medido em 2026-07-26)

O `up -d` só retorna quando tudo está `healthy`, porque o backend depende do healthcheck do
gtfs — que é o próprio *readiness* dele, e o readiness só liga depois do índice carregar:

```
redis, postgres      healthy em segundos
sppo-gtfs-service    índice do BigQuery em ~45 s  (457 linhas · 1.157 shapes)
sppo-tracker-backend healthy ~25 s depois
```

Primeira execução leva mais tempo (build das duas imagens Maven).

## Conferir que está funcionando

```bash
docker compose -f docker-compose.stack.yml --env-file .env.stack ps
curl -s http://localhost:8090/actuator/health              # {"status":"UP"}
curl -s http://localhost:8090/api/v1/gps-polling/status     # 204 se nenhum ciclo rodou ainda
docker logs sppo-tracker-backend-sppo-gtfs-service-1 | grep "index reloaded"
```

Contadores de classificação (acumulados desde a subida, não um retrato do instante):

```bash
curl -s http://localhost:8090/actuator/prometheus | grep gps_classifications_total
curl -s http://localhost:8090/actuator/prometheus | grep gps_route_shape_resolve_total
```

### Conferir especificamente o fora de rota

```bash
curl -s "http://localhost:8090/api/v1/vehicle-positions/current?classificationStatus=OUT_OF_ROUTE"
```

Duas checagens que separam "fora de rota de verdade" de "não consegui avaliar":

1. **`tags` deve conter só `OUT_OF_ROUTE`.** Se vier junto `ROUTE_UNRESOLVED` ou
   `ROUTE_NOT_PLANNED`, aquela posição **não** foi julgada fora de rota — a linha não tem
   geometria resolvida ou não está no planejamento vigente (§4.4).
2. **`gps_route_shape_resolve_total{result="empty"}` deve ficar em 0.** Se estiver subindo,
   o gtfs está respondendo sem shapes (índice ainda carregando, credencial errada, ou
   divergência de normalização do código de linha) — e aí o fora de rota fica cego.

No mapa, o seletor *Status de classificação → Fora de rota* filtra os marcadores laranja; o
filtro é client-side, não recarrega do servidor. O rodapé do painel mostra
`N visíveis · N na frota · N sem planejamento`.

Referência de uma execução real (2026-07-26, 16:11 BRT): 1.780 veículos na frota,
**87 fora de rota**, 37 sem planejamento, e `resolve` com 221 shapes, 0 vazios, 0 falhas.

## Derrubar

```bash
docker compose -f docker-compose.stack.yml --env-file .env.stack down      # preserva os volumes
docker compose -f docker-compose.stack.yml --env-file .env.stack down -v   # apaga Redis + Postgres
```

## Problemas comuns

- **Mapa vazio, polling falhando.** O feed da SMTR é instável: às vezes devolve página HTML
  de erro com HTTP 200. O provider trata isso como falha transitória e re-tenta; quando a
  fonte volta, o mapa preenche. Não é bug do backend (ver `docs/estado-do-projeto.md` §5).
- **Nenhum `OUT_OF_ROUTE` aparece.** Confira `ROUTE_SHAPE_SOURCE=gtfs-service` no
  `.env.stack` (o default do código é `disabled`) e se o índice do gtfs carregou.
- **`password authentication failed for user "sppotracker"`.** Volume do Postgres criado com
  outra senha; os dois compose compartilham volumes quando rodados da mesma pasta. Solução e
  detalhes em `docs/deploy-stack.md` (Troubleshooting).

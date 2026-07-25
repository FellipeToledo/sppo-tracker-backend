# Deploy do stack unificado (backend + gtfs-service + Redis + Postgres)

Sobe **os dois serviços** e as dependências com um `docker compose`, para qualquer host
com Docker (VPS, Oracle Cloud, etc.). Provedor-agnóstico: a escolha da infra é ortogonal.

Arquivo: **`docker-compose.stack.yml`** (na raiz deste repo). O backend é o único exposto
ao host; o `sppo-gtfs-service` fica interno na rede do compose e é acessado em
`http://sppo-gtfs-service:8080`.

## Pré-requisitos

- Docker + Docker Compose no host.
- Os **dois repositórios clonados lado a lado** (irmãos):

  ```
  parent/
    sppo-tracker-backend/   # rode o compose daqui
    sppo-gtfs-service/      # build context ../sppo-gtfs-service
  ```

  O caminho é ajustável por `GTFS_SERVICE_PATH` no `.env.stack`.
- Para BigQuery real: uma **service account** do GCP com acesso ao dataset
  `rj-smtr.planejamento` (BigQuery Data Viewer + Job User).

## Passo a passo

```bash
cd sppo-tracker-backend

# 1. Configuração
cp .env.stack.example .env.stack
# edite .env.stack: DB_PASSWORD, GTFS_API_KEY, GCP_PROJECT_ID, ...

# 2. Credenciais do GCP (BigQuery real)
mkdir -p secrets
cp /caminho/da/sua-sa.json secrets/gcp-sa.json     # montado read-only em /secrets

# 3. Subir
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build

# 4. Verificar
docker compose -f docker-compose.stack.yml --env-file .env.stack ps
curl -s http://localhost:8090/actuator/health
```

Abra o mapa em `http://SEU_HOST:8090/` (ajuste `SERVER_PORT` no `.env.stack`).

### Sem BigQuery (só para experimentar)

Defina `GTFS_PROFILE=demo` no `.env.stack` — o gtfs-service sobe com um loader de amostra
em memória, sem credenciais. O `secrets/` pode ficar vazio (só precisa existir).

## Configuração (`.env.stack`)

| Variável | Papel | Padrão |
|---|---|---|
| `SERVER_PORT` | porta pública do backend | `8090` |
| `ROUTE_SHAPE_SOURCE` | `gtfs-service` liga OUT_OF_ROUTE + desvios; `disabled` desliga | `gtfs-service` |
| `DB_NAME` / `DB_USER` / `DB_PASSWORD` | Postgres do backend (`DB_PASSWORD` obrigatório) | sppotracker / sppotracker / — |
| `GTFS_API_KEY` | chave `X-Api-Key` compartilhada backend ⇄ gtfs | — |
| `GTFS_PROFILE` | vazio = BigQuery real; `demo` = amostra | vazio |
| `GCP_PROJECT_ID` | projeto de billing das queries | — |
| `GTFS_VIAGEM_TABLE` / `GTFS_SHAPES_TABLE` | tabelas do GTFS | tabelas `rj-smtr.planejamento.*` |
| `BIGQUERY_LOCATION` | região de processamento | `US` |
| `GTFS_ADMIN_KEY` | chave do `/internal/reload` do gtfs | — |
| `GTFS_SERVICE_PATH` | build context do gtfs-service | `../sppo-gtfs-service` |

Credenciais (`.env.stack`, `secrets/`) **não** vão para o git (ver `.gitignore`).

## Recursos / memória

Limites definidos no compose (para caber num host de ~4 GB): backend 1 GB, gtfs 1 GB,
Postgres 512 MB, Redis 256 MB. As JVMs usam `-XX:MaxRAMPercentage=75`, então o heap
acompanha o `mem_limit`. Ajuste os limites conforme o host.

## Portas / firewall

- Exponha **apenas** a porta do backend (`SERVER_PORT`, ex. 8090) — ou coloque um
  reverse proxy (Caddy/Nginx) com TLS na frente (ver `docs/deploy-oracle-cloud.md`).
- O `sppo-gtfs-service` não precisa de porta pública (fica na rede interna do compose).

## Atualizações

```bash
git -C ../sppo-gtfs-service pull && git pull
docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
```

## Troubleshooting

- **`FATAL: password authentication failed for user "sppotracker"`** (backend em loop de
  restart): o volume do Postgres foi criado num run anterior com **outra** senha. O Postgres
  só aplica `POSTGRES_PASSWORD` na **primeira** inicialização do volume; mudar `DB_PASSWORD`
  depois não re-senha o banco existente. Corrija resetando os volumes (dado de teste):
  ```bash
  docker compose -f docker-compose.stack.yml --env-file .env.stack down -v
  docker compose -f docker-compose.stack.yml --env-file .env.stack up -d --build
  ```
  Em produção (dado real), em vez de apagar, mantenha o `DB_PASSWORD` estável ou altere a
  senha no próprio Postgres (`ALTER USER`).
- **Este e o `docker-compose.yml` (standalone) compartilham os volumes** quando rodados da
  mesma pasta (mesmo nome de projeto). Se alternar entre os dois com senhas diferentes,
  ocorre o erro acima.

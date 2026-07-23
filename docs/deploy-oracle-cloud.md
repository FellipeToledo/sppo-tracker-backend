# Deploy — Oracle Cloud (Always Free)

Guia para subir o `sppo-tracker-backend` numa **VM ARM Ampere da Oracle Cloud
(Always Free)** com `docker compose`. A stack é **autossuficiente** (backend +
Redis + Postgres) e **não tem segredo** — a API de GPS do SPPO é pública.

> A pegadinha da Oracle são os **dois firewalls**: a *Security List* da subnet
> **e** o `iptables` local do Ubuntu. Se esquecer de um, a porta parece fechada.
> Veja a §4.

---

## 1. Provisionar a VM

- **Shape:** `VM.Standard.A1.Flex` — **2 OCPU / 12 GB** cabem no Always Free
  (o limite gratuito é 4 OCPU / 24 GB Ampere; use metade e sobra folga).
- **Imagem:** Ubuntu 22.04 (ARM64).
- **Rede:** use uma VCN com subnet pública e IP público (efêmero ou reservado).
- **Chave SSH:** guarde a chave privada; acesso `ssh ubuntu@<IP_PUBLICO>`.

## 2. Instalar Docker + Compose plugin

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl git
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | \
  sudo tee /etc/apt/keyrings/docker.asc >/dev/null
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" | \
  sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
sudo usermod -aG docker ubuntu   # relogar para valer sem sudo
```

## 3. Clonar e configurar

```bash
git clone https://github.com/FellipeToledo/sppo-tracker-backend.git
cd sppo-tracker-backend
cp .env.example .env
# edite o .env: troque DB_PASSWORD; ajuste SERVER_PORT se quiser.
```

## 4. Liberar os DOIS firewalls

Escolha a porta que vai expor. Sem TLS, é a `SERVER_PORT` (default **8090**);
com TLS via Caddy (§6), são a **80** e a **443**.

**(a) Security List da subnet** (Console Oracle → Networking → VCN → Subnet →
Security List → *Add Ingress Rule*):

- Source `0.0.0.0/0`, IP Protocol `TCP`, Destination Port `8090`
  (ou `80,443` se usar TLS).

**(b) `iptables` local do Ubuntu** — a imagem da Oracle vem com `INPUT` fechado:

```bash
sudo iptables -I INPUT 6 -p tcp --dport 8090 -j ACCEPT   # ajuste a porta
sudo netfilter-persistent save                            # persiste no reboot
# (para TLS: repita para 80 e 443)
```

## 5. Subir a stack

```bash
# base (expõe SERVER_PORT):
docker compose up -d --build

# ou com o overlay de produção (limites de memória p/ o A1.Flex):
docker compose -f docker-compose.yml -f docker-compose.oracle.yml up -d --build
```

Verifique:

```bash
curl -s http://localhost:8090/actuator/health          # {"status":"UP"}
curl -s http://<IP_PUBLICO>:8090/actuator/health       # de fora da VM
curl -s http://localhost:8090/actuator/prometheus | head
```

O `restart: unless-stopped` já garante que a stack volte após reboot; os volumes
`postgres-data`/`redis-data` são persistentes.

## 6. HTTPS opcional (Caddy)

Com um domínio apontando (registro A) para o IP público da VM:

```bash
# no .env: DOMAIN=sppo.seu-dominio.com  (LETSENCRYPT_EMAIL é opcional)
docker compose -f docker-compose.yml -f docker-compose.oracle.yml \
  --profile tls up -d
```

O Caddy obtém e renova o certificado automaticamente (portas 80/443 liberadas nos
dois firewalls — §4). Ele faz proxy reverso para o backend em `:8080` interno.

## 7. Operação

```bash
docker compose logs -f sppo-tracker-backend      # logs
docker compose ps                                # status/health
git pull && docker compose up -d --build         # atualizar para a última versão
docker compose down                              # derrubar (volumes preservados)
```

**Backup dos volumes** (Postgres é o que importa — histórico de desvio):

```bash
docker run --rm -v sppo-tracker-backend_postgres-data:/data -v "$PWD":/backup \
  alpine tar czf /backup/postgres-data.tgz -C /data .
```

## 8. Observabilidade (opcional)

O backend já expõe `/actuator/prometheus`. Para um Prometheus + Grafana no mesmo
compose, adicione os serviços num overlay próprio apontando o scrape para
`sppo-tracker-backend:8080`. Fica como evolução futura (ver
`docs/regras-de-negocio.md` §10).

# Aproveitando o máximo do Claude Code desde o início (na Antigravity)

Guia prático para começar um projeto **do zero** já otimizado para trabalhar com
o Claude Code (CLI/agente) — inclusive dentro da IDE Antigravity, onde você quer
controle local e remoto. A ideia central: **deixe o repositório se auto-descrever**
para que qualquer sessão do agente comece com contexto, saiba rodar/verificar o
código, e siga os seus padrões sem você repetir instruções.

> Regra de ouro: **quanto mais o repositório ensina o agente, menos você digita.**
> Tudo aqui é versionado — beneficia todas as sessões futuras e todo o time.

---

## 0. Os 5 arquivos que definem tudo (crie no primeiro commit)

| Arquivo | Para quê |
|---|---|
| `CLAUDE.md` | Memória do projeto: arquitetura, comandos, convenções, pegadinhas. **O arquivo de maior alavancagem.** |
| `.claude/settings.json` | Permissões (allowlist), env vars e **hooks** do harness. |
| `.claude/hooks/session-start.sh` | Prepara o ambiente a cada sessão (build/deps/tests prontos). |
| `docs/regras-de-negocio.md` | A especificação funcional (fonte da verdade do domínio). |
| `README.md` | Porta de entrada humana; comandos essenciais. |

---

## 1. `CLAUDE.md` — trate como código de produção

É o primeiro contexto que o agente lê. Um bom `CLAUDE.md` responde, em minutos:
*o que é o projeto, como rodar, como testar, como está organizado, o que NÃO fazer.*

**Inclua:**
- **Visão geral** em 3–4 linhas (o que o serviço faz e o que ele é).
- **Comandos** exatos: build, testes (todos / um só), rodar local, lint, deploy.
- **Arquitetura**: camadas, dependências (para onde apontam), onde fica cada coisa.
- **Convenções**: estilo de commit, tratamento de erro, nomes, onde ficam testes.
- **Pegadinhas/decisões**: quirks do provider, fusos, precedências, "não mexer em X".

**Mantenha vivo:** toda vez que uma convenção mudar, atualize o `CLAUDE.md` no
mesmo PR. Um `CLAUDE.md` desatualizado ensina o agente a errar.

> Dica: rode `/init` numa base existente para gerar um rascunho, depois refine à mão.

---

## 2. Deixe o agente **verificar** o próprio trabalho

O Claude Code é muito mais eficaz quando consegue **rodar e observar** o resultado,
não só escrever código. Garanta desde o commit 1:

- **Testes que rodam com um comando** (`mvn test`, `npm test`, …) e **rápido**.
- Um **`SessionStart` hook** (`.claude/hooks/session-start.sh`) que baixa deps e
  deixa o build pronto — assim a sessão já começa capaz de compilar/testar.
  *(A skill `session-start-hook` do Claude Code ajuda a montar isso.)*
- **TDD-friendly:** peça "escreva o teste que falha primeiro, depois implemente".
- Use as skills **`/verify`** (exercita a mudança de ponta a ponta) e **`/run`**
  (sobe a app) antes de dar algo como pronto.

---

## 3. Reduza atrito de permissões (`.claude/settings.json`)

Cada comando novo pede aprovação. Faça uma **allowlist** dos comandos seguros e
frequentes (build, testes, git status/diff, ls, grep) para o agente fluir sem
interromper você a cada passo.

- Comece permissivo com **leitura** (`Read`, `Grep`, `ls`, `git status/log/diff`)
  e com os comandos de build/test do projeto.
- Mantenha aprovação manual para o que é **destrutivo/externo** (push, deploy,
  `rm -rf`, chamadas de rede novas).
- A skill **`/fewer-permission-prompts`** analisa seu histórico e sugere a
  allowlist automaticamente depois de um tempo de uso.

---

## 4. Planeje antes de mexer (Plan Mode)

Para qualquer tarefa não-trivial, peça um **plano antes da edição**:
- "Entre em plan mode e me proponha a abordagem antes de escrever código."
- Você revisa o plano, ajusta, e só então o agente executa. Evita retrabalho e
  mudanças de arquitetura no susto.

---

## 5. Automatize workflows repetidos (Skills / Slash commands)

Se você faz algo mais de duas vezes, vire uma **skill** (`.claude/skills/<nome>/SKILL.md`)
ou um slash command. Exemplos para este projeto:
- `/deploy` — passos do deploy na VM Oracle (build, compose up, health-check).
- `/new-rule` — checklist para adicionar uma regra de classificação (implementar,
  registrar no pipeline, atualizar precedência, testar).
- `/review` antes de PR: rode **`/code-review`** (bugs) e **`/security-review`**.

Skills versionadas = o agente executa seu processo do seu jeito, sempre.

---

## 6. Conecte ferramentas externas (MCP)

Configure servidores **MCP** desde cedo para o agente agir além do código:
- **GitHub MCP** — abrir/revisar PRs, checar CI, comentar, ler issues.
- Bancos, observabilidade, etc., conforme necessário.
Isso permite o "controle remoto" que você quer: o agente cria PR, acompanha CI e
corrige, sem você sair da IDE.

---

## 7. Use subagentes para tarefas amplas

Para pesquisa/refino paralelos (varrer o código, investigar várias hipóteses),
peça explicitamente um **subagente** (ex.: `Explore`, `Plan`, `general-purpose`).
Bom para não poluir o contexto principal com buscas grandes.

---

## 8. Higiene de contexto (o que mais acelera no dia a dia)

- **Uma tarefa por sessão.** Tarefas grandes → quebre; sessões enxutas rendem mais.
- **Referencie por caminho** (`src/.../Foo.java:42`) — é clicável e preciso.
- **Peça para ler antes de escrever** arquivos que ele não conhece.
- **Commits pequenos e verificáveis** (Conventional Commits): fica fácil revisar,
  reverter e o agente entende o histórico.
- **Não re-explique** o que já está no `CLAUDE.md`; melhore o `CLAUDE.md`.

---

## 9. Fluxo remoto (PRs, CI, "babysitting")

- Peça para **abrir o PR** e depois **acompanhar o PR** (o Claude Code assina os
  eventos do PR: comentários de revisão e falhas de CI, e corrige).
- Deixe a CI verde ser o "definition of done" de cada mudança.
- Para deploy contínuo, um workflow simples (build/test → publish imagem →
  deploy na VM) fecha o ciclo.

---

## 10. Checklist do "dia 0" (ordem sugerida)

1. `git init` + repositório novo no GitHub (privado).
2. Committar o **esqueleto** (este) com `CLAUDE.md`, `.claude/`, `docs/`, build.
3. Configurar `.claude/settings.json` (allowlist + hook de SessionStart).
4. Rodar o `SessionStart` hook e confirmar que **testes passam** do zero.
5. Conectar **GitHub MCP**.
6. Criar as primeiras **skills** (`/deploy`, `/new-rule`).
7. Trabalhar em **plan mode → implementar → `/verify` → `/code-review` → PR**.
8. Manter `CLAUDE.md` e `docs/` atualizados a cada mudança de convenção.

---

## Antigravity — observações

- A Antigravity te dá o loop agêntico local+remoto; o que a torna poderosa **com**
  o Claude é o repositório bem descrito: `CLAUDE.md`, testes rápidos, hooks e
  skills valem para qualquer sessão/agente.
- Mantenha o **"definition of done" objetivo** (testes + CI verdes) para o agente
  ter um alvo claro ao iterar sozinho.
- Versione `.claude/` no repo: assim o comportamento é reproduzível em qualquer
  máquina/sessão, local ou remota.

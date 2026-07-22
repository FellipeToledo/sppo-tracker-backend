# Contribuindo

- **Commits:** Conventional Commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`), em inglês.
- **Testes:** toda mudança de comportamento acompanha teste (`mvn --batch-mode test`).
- **Arquitetura:** hexagonal — o domínio não depende de framework; casos de uso dependem de portas.
- **Regras de negócio:** a fonte da verdade é `docs/regras-de-negocio.md`. Ao alterar uma regra de
  classificação, atualize a precedência no pipeline e os testes correspondentes.
- **PRs:** mantenha-os pequenos e com CI verde. Rode `/code-review` antes de abrir.

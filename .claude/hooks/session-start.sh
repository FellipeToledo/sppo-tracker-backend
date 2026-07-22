#!/usr/bin/env bash
# Prepara a sessão: garante deps baixadas para o agente já poder compilar/testar.
# Mantém rápido e silencioso; não falha a sessão se o build offline não completar.
set -uo pipefail
if command -v mvn >/dev/null 2>&1; then
  mvn -q --batch-mode dependency:go-offline >/dev/null 2>&1 || true
fi
echo "SessionStart: ambiente pronto (mvn test para rodar a suíte)."

#!/usr/bin/env bash
# Baixa as bulas oficiais da lista (scripts/bulas/lista.txt) e monta www/bulas.json.
#
# Rode no SEU computador (Linux, Mac ou Windows com WSL): a API do Bulário da Anvisa bloqueia os
# servidores do GitHub Actions, então isto não roda no build automático.
# Precisa de: git, Python 3.11+, uv (https://docs.astral.sh/uv/) e pdftotext (pacote poppler-utils).
# Usa o buladiff (https://github.com/vasfvitor/buladiff, licença MIT) para baixar e extrair as bulas.
#
#   bash scripts/bulas/baixar-bulas.sh            # baixa a lista inteira
#   bash scripts/bulas/baixar-bulas.sh 118190215  # só um registro
set -euo pipefail
APP="$(cd "$(dirname "$0")/../.." && pwd)"
DIR="${BULADIFF_DIR:-$HOME/.cache/buladiff}"
if [ ! -d "$DIR/.git" ]; then git clone --depth 1 https://github.com/vasfvitor/buladiff "$DIR"; fi
cd "$DIR"
uv sync --quiet
if [ "$#" -gt 0 ]; then regs="$*"; else regs="$(sed -e 's/#.*//' "$APP/scripts/bulas/lista.txt" | tr -s ' \n' ' ')"; fi
falhas=0
for r in $regs; do
  # a API da Anvisa dá erro 500 de vez em quando: tenta de novo uma vez
  uv run bulario fetch "$r" >/dev/null 2>&1 || { sleep 5; uv run bulario fetch "$r" >/dev/null 2>&1 || { echo "falhou: $r"; falhas=$((falhas+1)); }; }
  sleep 1
done
python3 "$APP/scripts/bulas/montar_bulas.py" "$DIR/data"
echo "Pronto. $falhas registro(s) com falha. Confira www/bulas.json e faça o commit."

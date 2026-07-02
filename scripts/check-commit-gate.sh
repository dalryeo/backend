#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

if [[ $# -ne 1 ]]; then
  echo "Usage: scripts/check-commit-gate.sh <commit-message-file>" >&2
  exit 2
fi

"$ROOT_DIR/scripts/check-sensitive-paths.sh" --staged
"$ROOT_DIR/scripts/check-commit-message.sh" "$1"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"
chmod +x "$ROOT_DIR/.githooks/commit-msg"

if [[ "${DALRYEO_INSTALL_HOOKS_SKIP_GIT_CONFIG:-}" == "1" ]]; then
  echo "install-git-hooks: core.hooksPath update skipped"
else
  git config core.hooksPath .githooks
  echo "install-git-hooks: core.hooksPath set to .githooks"
fi

echo "install-git-hooks: local hooks are fast feedback; CI and branch protection remain authoritative"

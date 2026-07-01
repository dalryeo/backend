#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"
git config core.hooksPath .githooks

echo "install-git-hooks: core.hooksPath set to .githooks"
echo "install-git-hooks: local hooks are fast feedback; CI and branch protection remain authoritative"

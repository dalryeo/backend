#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env.test"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck source=/dev/null
  source "$ENV_FILE"
  set +a
fi

: "${TEST_DB_URL:?TEST_DB_URL is required. Set it in .env.test or export it.}"
: "${TEST_DB_USERNAME:?TEST_DB_USERNAME is required. Set it in .env.test or export it.}"
: "${TEST_DB_PASSWORD:?TEST_DB_PASSWORD is required. Set it in .env.test or export it.}"

cd "$ROOT_DIR"
exec ./gradlew --no-daemon test "$@"

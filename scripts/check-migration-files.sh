#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/check-migration-files.sh [--directory <migration-dir>]
USAGE
}

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
migration_dir="$ROOT_DIR/src/main/resources/db/migration"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --directory)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      migration_dir="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      usage
      exit 2
      ;;
  esac
done

fail() {
  echo "check-migration-files: $1" >&2
  exit 1
}

[[ -d "$migration_dir" ]] || fail "missing migration directory: $migration_dir"

declare -a seen_versions=()
declare -a seen_names=()
has_files=false

while IFS= read -r -d '' file; do
  has_files=true
  name="$(basename "$file")"

  [[ "$name" =~ ^V([1-9][0-9]*)__[a-z0-9_]+\.sql$ ]] \
    || fail "invalid migration filename: $name"

  version="${BASH_REMATCH[1]}"
  for index in "${!seen_versions[@]}"; do
    if [[ "${seen_versions[$index]}" == "$version" ]]; then
      fail "duplicate migration version V$version: ${seen_names[$index]} and $name"
    fi
  done

  seen_versions+=("$version")
  seen_names+=("$name")
done < <(find "$migration_dir" -maxdepth 1 -type f -print0)

[[ "$has_files" == true ]] || fail "no migration files found in $migration_dir"

echo "check-migration-files: OK"

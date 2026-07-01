#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/check-sensitive-paths.sh [--tracked|--staged|--paths-file <file>]

Checks path names only. File contents are never read.
USAGE
}

mode="tracked"
paths_file=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --tracked)
      mode="tracked"
      shift
      ;;
    --staged)
      mode="staged"
      shift
      ;;
    --paths-file)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      mode="paths-file"
      paths_file="$2"
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

collect_paths() {
  case "$mode" in
    tracked)
      git -c core.quotePath=false ls-files
      ;;
    staged)
      git -c core.quotePath=false diff --cached --name-only --diff-filter=ACMR
      ;;
    paths-file)
      [[ -f "$paths_file" ]] || {
        echo "check-sensitive-paths: missing paths file: $paths_file" >&2
        exit 2
      }
      cat "$paths_file"
      ;;
  esac
}

is_allowed_env_example() {
  local path="$1"
  local lower_path

  lower_path="$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')"
  [[ "$lower_path" == ".env.example" || "$lower_path" =~ (^|/)\.env\..*\.example$ ]]
}

is_forbidden_path() {
  local path="$1"
  local lower_path
  local lower_base

  lower_path="$(printf '%s' "$path" | tr '[:upper:]' '[:lower:]')"
  lower_base="${lower_path##*/}"

  [[ -z "$path" ]] && return 1

  if [[ "$lower_base" == ".ds_store" ]]; then
    return 0
  fi

  if [[ "$lower_path" =~ (^|/)\.env($|\.) ]]; then
    is_allowed_env_example "$path" && return 1
    return 0
  fi

  if [[ "$lower_path" =~ \.(pem|key|p12|jks|keystore)$ ]]; then
    return 0
  fi

  return 1
}

declare -a failures=()

while IFS= read -r path; do
  if is_forbidden_path "$path"; then
    failures+=("$path")
  fi
done < <(collect_paths)

if [[ ${#failures[@]} -gt 0 ]]; then
  echo "check-sensitive-paths: forbidden tracked/staged path(s):" >&2
  printf '  %s\n' "${failures[@]}" >&2
  exit 1
fi

echo "check-sensitive-paths: OK"

#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/check-commit-message.sh <commit-message-file>
  scripts/check-commit-message.sh --subject-only --subject <subject>
USAGE
}

subject_only=false
subject=""
message_file=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --subject-only)
      subject_only=true
      shift
      ;;
    --subject)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      subject="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      if [[ -z "$message_file" ]]; then
        message_file="$1"
        shift
      else
        usage
        exit 2
      fi
      ;;
  esac
done

fail() {
  echo "check-commit-message: $1" >&2
  exit 1
}

validate_subject() {
  local value="$1"

  [[ "$value" =~ ^(feat|fix|refactor|test|docs|chore|perf):\ [[:space:]]*[^[:space:]] ]] \
    || fail "subject must use 'type: 제목' with allowed type and non-empty title"

  [[ ! "$value" =~ \.[[:space:]]*$ ]] \
    || fail "subject must not end with a period"
}

if [[ "$subject_only" == true ]]; then
  [[ -n "$subject" ]] || fail "subject is required"
  validate_subject "$subject"
  echo "check-commit-message: OK"
  exit 0
fi

[[ -n "$message_file" ]] || { usage; exit 2; }
[[ -f "$message_file" ]] || {
  echo "check-commit-message: missing commit message file: $message_file" >&2
  exit 2
}

declare -a lines=()
while IFS= read -r line || [[ -n "$line" ]]; do
  [[ "$line" == \#* ]] && continue
  lines+=("$line")
done < "$message_file"

[[ ${#lines[@]} -ge 1 ]] || fail "commit message is empty"

subject="${lines[0]}"
validate_subject "$subject"

[[ ${#lines[@]} -ge 3 ]] || fail "commit body is required"
[[ -z "${lines[1]}" ]] || fail "subject and body must be separated by one blank line"

has_body=false
has_bullet=false

for ((i = 2; i < ${#lines[@]}; i++)); do
  line="${lines[$i]}"
  if [[ -n "${line//[[:space:]]/}" ]]; then
    has_body=true
  fi
  if [[ "$line" =~ ^-\  ]]; then
    has_bullet=true
  fi
done

[[ "$has_body" == true ]] || fail "commit body is required"
[[ "$has_bullet" == true ]] || fail "commit body must contain at least one '- ' bullet"

echo "check-commit-message: OK"

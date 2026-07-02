#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/check-pr-contract.sh [--title <title>] [--body-file <file>] [--changed-files <file>]

When --title or --body-file is omitted in GitHub Actions, values are read from
GITHUB_EVENT_PATH. Changed files default to git diff against origin/$GITHUB_BASE_REF.
USAGE
}

title=""
body_file=""
changed_files=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --title)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      title="$2"
      shift 2
      ;;
    --body-file)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      body_file="$2"
      shift 2
      ;;
    --changed-files)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      changed_files="$2"
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

tmp_dir="$(mktemp -d)"
trap 'rm -rf "$tmp_dir"' EXIT

if [[ -z "$title" || -z "$body_file" ]]; then
  [[ -n "${GITHUB_EVENT_PATH:-}" && -f "${GITHUB_EVENT_PATH:-}" ]] || {
    echo "check-pr-contract: title/body-file required outside GitHub Actions" >&2
    exit 2
  }

  event_title="$tmp_dir/pr-title.txt"
  event_body="$tmp_dir/pr-body.md"
  python3 - "$GITHUB_EVENT_PATH" "$event_title" "$event_body" <<'PY'
import json
import sys
from pathlib import Path

event = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
pull_request = event.get("pull_request") or {}
Path(sys.argv[2]).write_text(pull_request.get("title") or "", encoding="utf-8")
Path(sys.argv[3]).write_text(pull_request.get("body") or "", encoding="utf-8")
PY

  [[ -n "$title" ]] || title="$(cat "$event_title")"
  [[ -n "$body_file" ]] || body_file="$event_body"
fi

if [[ -z "$changed_files" ]]; then
  changed_files="$tmp_dir/changed-files.txt"
  if [[ -n "${GITHUB_BASE_REF:-}" ]]; then
    git diff --name-only "origin/${GITHUB_BASE_REF}...HEAD" >"$changed_files"
  else
    git diff --name-only HEAD~1..HEAD >"$changed_files"
  fi
fi

"$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/scripts/check-commit-message.sh" \
  --subject-only \
  --subject "$title"

python3 - "$body_file" "$changed_files" <<'PY'
from pathlib import Path
import re
import sys

body_file = Path(sys.argv[1])
changed_files = Path(sys.argv[2])

body = body_file.read_text(encoding="utf-8")
changed = changed_files.read_text(encoding="utf-8").splitlines()

failures = []


def strip_comments(text):
    return re.sub(r"<!--.*?-->", "", text, flags=re.DOTALL)


def sections(markdown):
    result = {}
    current = None
    collected = []

    for line in markdown.splitlines():
        match = re.match(r"^##\s+(.+?)\s*$", line)
        if match:
            if current is not None:
                result[current] = "\n".join(collected)
            current = match.group(1).strip()
            collected = []
        elif current is not None:
            collected.append(line)

    if current is not None:
        result[current] = "\n".join(collected)

    return result


def is_meaningful(text):
    cleaned = strip_comments(text)
    lines = [line.strip() for line in cleaned.splitlines() if line.strip()]
    if not lines:
        return False
    return any(line not in {"-", "- [ ]", "- [x]"} for line in lines)


def has_checked_line(text, expected):
    cleaned = strip_comments(text)
    pattern = re.compile(r"^\s*-\s+\[[xX]\]\s+" + re.escape(expected) + r"\s*$", re.MULTILINE)
    return bool(pattern.search(cleaned))


parsed = sections(body)
required_sections = [
    "작업 내용",
    "변경 이유",
    "검증",
    "운영 영향",
    "계약 영향",
    "마이그레이션 영향 (DB 변경 시에만)",
    "리뷰 포인트",
]

for section in required_sections:
    if section not in parsed:
        failures.append(f"missing PR section: ## {section}")

for section in ("작업 내용", "변경 이유", "검증", "운영 영향", "리뷰 포인트"):
    if section in parsed and not is_meaningful(parsed[section]):
        failures.append(f"empty PR section: ## {section}")

contract_text = "API 요청/응답 필드, status code, error shape 변경 여부를 확인함"
if "계약 영향" in parsed and not has_checked_line(parsed["계약 영향"], contract_text):
    failures.append("contract impact checkbox must be checked")

db_changed = any(re.search(r"(^|/)db/migration/", path) for path in changed)
if db_changed and "마이그레이션 영향 (DB 변경 시에만)" in parsed:
    migration_checks = [
        "DB 변경이 하위 호환인지 검토함",
        "코드-스키마 배포 순서와 롤백 영향을 PR에 적음",
    ]
    for check in migration_checks:
        if not has_checked_line(parsed["마이그레이션 영향 (DB 변경 시에만)"], check):
            failures.append(f"migration checkbox must be checked: {check}")

if failures:
    for failure in failures:
        print(f"check-pr-contract: {failure}", file=sys.stderr)
    sys.exit(1)

print("check-pr-contract: OK")
PY

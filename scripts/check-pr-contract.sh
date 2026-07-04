#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat >&2 <<'USAGE'
Usage:
  scripts/check-pr-contract.sh [--title <title>] [--body-file <file>] [--changed-files <file>] [--base-ref <branch>]

When --title or --body-file is omitted in GitHub Actions, values are read from
GITHUB_EVENT_PATH. Changed files default to git diff against origin/$GITHUB_BASE_REF.
USAGE
}

title=""
body_file=""
changed_files=""
base_ref="${GITHUB_BASE_REF:-}"

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
    --base-ref)
      [[ $# -ge 2 ]] || { usage; exit 2; }
      base_ref="$2"
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
  event_base_ref="$tmp_dir/pr-base-ref.txt"
  python3 - "$GITHUB_EVENT_PATH" "$event_title" "$event_body" "$event_base_ref" <<'PY'
import json
import sys
from pathlib import Path

event = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
pull_request = event.get("pull_request") or {}
Path(sys.argv[2]).write_text(pull_request.get("title") or "", encoding="utf-8")
Path(sys.argv[3]).write_text(pull_request.get("body") or "", encoding="utf-8")
Path(sys.argv[4]).write_text((pull_request.get("base") or {}).get("ref") or "", encoding="utf-8")
PY

  [[ -n "$title" ]] || title="$(cat "$event_title")"
  [[ -n "$body_file" ]] || body_file="$event_body"
  [[ -n "$base_ref" ]] || base_ref="$(cat "$event_base_ref")"
fi

if [[ -z "$changed_files" ]]; then
  changed_files="$tmp_dir/changed-files.txt"
  if [[ -n "$base_ref" ]]; then
    git diff --name-status "origin/${base_ref}...HEAD" >"$changed_files"
  else
    git diff --name-status HEAD~1..HEAD >"$changed_files"
  fi
fi

"$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/scripts/check-commit-message.sh" \
  --subject-only \
  --subject "$title"

python3 - "$body_file" "$changed_files" "$base_ref" <<'PY'
from pathlib import Path
import re
import sys

body_file = Path(sys.argv[1])
changed_files = Path(sys.argv[2])
base_ref = sys.argv[3].strip()

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


def checked_count(text, expected_lines):
    return sum(1 for expected in expected_lines if has_checked_line(text, expected))


def parse_changed_entries(lines):
    entries = []
    for raw in lines:
        line = raw.strip()
        if not line:
            continue

        parts = line.split("\t")
        if len(parts) >= 2 and re.match(r"^(A|M|D|R\d*|C\d*)$", parts[0]):
            entries.append((parts[0], parts[1:]))
        else:
            entries.append(("?", [line]))
    return entries


def is_migration_path(path):
    return bool(re.search(r"(^|/)db/migration/", path))


parsed = sections(body)
required_sections = [
    "작업 내용",
    "대상 브랜치",
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

target_branch_choices = [
    "일반 작업: `dev` 대상 PR",
    "운영 배포 승격: `dev` -> `main`",
    "운영 긴급 수정: `hotfix/*` -> `main`, 배포 후 `dev` 반영 계획 작성",
]
main_target_choices = target_branch_choices[1:]
main_target_checked = False
if "대상 브랜치" in parsed:
    checked_targets = [
        target
        for target in target_branch_choices
        if has_checked_line(parsed["대상 브랜치"], target)
    ]
    if len(checked_targets) != 1:
        failures.append("exactly one target branch checkbox must be checked")
    elif base_ref == "dev" and checked_targets[0] != target_branch_choices[0]:
        failures.append("target branch checkbox must match actual PR base: dev")
    elif base_ref == "main" and checked_targets[0] not in main_target_choices:
        failures.append("target branch checkbox must match actual PR base: main")

    if base_ref == "main":
        main_target_checked = True
    elif base_ref == "dev":
        main_target_checked = False
    else:
        main_target_checked = bool(checked_targets and checked_targets[0] in main_target_choices)

contract_text = "API 계약 영향 범위를 확인함"
compatibility_choices = [
    "하위 호환 영향 없음",
    "하위 호환 영향 있음 - 운영 영향 또는 리뷰 포인트에 앱 클라이언트 영향과 대응 계획을 적음",
]
app_client_choices = [
    "앱 클라이언트 조율 필요 없음",
    "앱 클라이언트 조율 필요 - main 승격 전 조율 계획을 적음",
]
promotion_contract_test_text = "운영 배포 승격 전 API 계약 테스트 영향 확인함"
if "계약 영향" in parsed:
    contract_section = parsed["계약 영향"]
    if not has_checked_line(contract_section, contract_text):
        failures.append("contract impact checkbox must be checked")
    if checked_count(contract_section, compatibility_choices) != 1:
        failures.append("exactly one API compatibility checkbox must be checked")
    if checked_count(contract_section, app_client_choices) != 1:
        failures.append("exactly one app client coordination checkbox must be checked")
    if main_target_checked and not has_checked_line(contract_section, promotion_contract_test_text):
        failures.append("main-target PR must check API contract test impact")

changed_entries = parse_changed_entries(changed)
db_entries = [
    (status, paths)
    for status, paths in changed_entries
    if any(is_migration_path(path) for path in paths)
]
db_changed = bool(db_entries)
if db_changed and "마이그레이션 영향 (DB 변경 시에만)" in parsed:
    migration_checks = [
        "DB 변경이 하위 호환인지 검토함",
        "코드-스키마 배포 순서와 롤백 영향을 PR에 적음",
    ]
    for check in migration_checks:
        if not has_checked_line(parsed["마이그레이션 영향 (DB 변경 시에만)"], check):
            failures.append(f"migration checkbox must be checked: {check}")

    migration_change_types = [
        "expand",
        "backfill",
        "switch",
        "contract",
        "seed/reference data",
    ]
    if not any(has_checked_line(parsed["마이그레이션 영향 (DB 변경 시에만)"], change_type)
               for change_type in migration_change_types):
        failures.append("one migration change type checkbox must be checked")

    for status, paths in db_entries:
        if status != "?" and status != "A":
            migration_paths = ", ".join(path for path in paths if is_migration_path(path))
            failures.append(
                "existing migration files must not be modified, deleted, or renamed: "
                + migration_paths
            )

if failures:
    for failure in failures:
        print(f"check-pr-contract: {failure}", file=sys.stderr)
    sys.exit(1)

print("check-pr-contract: OK")
PY

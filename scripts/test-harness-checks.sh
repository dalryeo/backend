#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TMP_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMP_ROOT"' EXIT

fail() {
  echo "test-harness-checks: $1" >&2
  exit 1
}

assert_pass() {
  local name="$1"
  shift
  if ! "$@" >"$TMP_ROOT/${name}.out" 2>"$TMP_ROOT/${name}.err"; then
    cat "$TMP_ROOT/${name}.out" >&2 || true
    cat "$TMP_ROOT/${name}.err" >&2 || true
    fail "expected pass: $name"
  fi
}

assert_fail() {
  local name="$1"
  shift
  if "$@" >"$TMP_ROOT/${name}.out" 2>"$TMP_ROOT/${name}.err"; then
    cat "$TMP_ROOT/${name}.out" >&2 || true
    fail "expected failure: $name"
  fi
}

write_file() {
  local path="$1"
  shift
  printf '%s\n' "$@" >"$path"
}

test_sensitive_paths() {
  local paths_file="$TMP_ROOT/paths.txt"

  write_file "$paths_file" \
    ".env.test.example" \
    ".env.example" \
    "src/main/java/App.java"
  assert_pass sensitive_allows_examples "$ROOT_DIR/scripts/check-sensitive-paths.sh" --paths-file "$paths_file"

  write_file "$paths_file" \
    ".env" \
    "src/main/resources/static/.DS_Store" \
    "secrets/prod.pem"
  assert_fail sensitive_blocks_forbidden "$ROOT_DIR/scripts/check-sensitive-paths.sh" --paths-file "$paths_file"
}

test_commit_message() {
  local good="$TMP_ROOT/good-commit.txt"
  local bad_subject="$TMP_ROOT/bad-subject.txt"
  local bad_body="$TMP_ROOT/bad-body.txt"

  write_file "$good" \
    "chore: 하네스 검사 추가" \
    "" \
    "- 커밋 메시지 구조 검사 추가" \
    "- 민감 경로 검사 추가"
  assert_pass commit_message_good "$ROOT_DIR/scripts/check-commit-message.sh" "$good"

  write_file "$bad_subject" \
    "Chore: 하네스 검사 추가" \
    "" \
    "- 커밋 메시지 구조 검사 추가"
  assert_fail commit_message_bad_subject "$ROOT_DIR/scripts/check-commit-message.sh" "$bad_subject"

  write_file "$bad_body" \
    "chore: 하네스 검사 추가" \
    "" \
    "커밋 메시지 구조 검사 추가"
  assert_fail commit_message_requires_bullet "$ROOT_DIR/scripts/check-commit-message.sh" "$bad_body"
}

test_pr_contract() {
  local changed_files="$TMP_ROOT/changed-files.txt"
  local body="$TMP_ROOT/pr-body.md"

  write_file "$changed_files" \
    "scripts/check-pr-contract.sh"
  write_file "$body" \
    "## 작업 내용" \
    "- PR 검증 스크립트 추가" \
    "" \
    "## 변경 이유" \
    "- 자동 검증 누락 방지" \
    "" \
    "## 검증" \
    "- bash scripts/test-harness-checks.sh" \
    "" \
    "## 운영 영향" \
    "- 없음" \
    "" \
    "## 계약 영향" \
    "- [x] API 요청/응답 필드, status code, error shape 변경 여부를 확인함" \
    "" \
    "## 마이그레이션 영향 (DB 변경 시에만)" \
    "- [ ] DB 변경이 하위 호환인지 검토함" \
    "- [ ] 코드-스키마 배포 순서와 롤백 영향을 PR에 적음" \
    "" \
    "## 리뷰 포인트" \
    "- 스크립트 실패 조건"
  assert_pass pr_contract_non_db "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files"

  write_file "$changed_files" \
    "src/main/resources/db/migration/V3__add_index.sql"
  assert_fail pr_contract_db_requires_checked_migration "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files"

  write_file "$body" \
    "## 작업 내용" \
    "- DB migration 추가" \
    "" \
    "## 변경 이유" \
    "- 조회 성능 보완" \
    "" \
    "## 검증" \
    "- bash scripts/test-harness-checks.sh" \
    "" \
    "## 운영 영향" \
    "- migration 적용 필요" \
    "" \
    "## 계약 영향" \
    "- [x] API 요청/응답 필드, status code, error shape 변경 여부를 확인함" \
    "" \
    "## 마이그레이션 영향 (DB 변경 시에만)" \
    "- [x] DB 변경이 하위 호환인지 검토함" \
    "- [x] 코드-스키마 배포 순서와 롤백 영향을 PR에 적음" \
    "" \
    "## 리뷰 포인트" \
    "- migration 배포 순서"
  assert_pass pr_contract_db_checked "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files"
}

test_sensitive_paths
test_commit_message
test_pr_contract

echo "test-harness-checks: OK"

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

mark() {
  if [[ "$1" == "$2" ]]; then
    printf 'x'
  else
    printf ' '
  fi
}

write_pr_body() {
  local path="$1"
  local target="${2:-dev}"
  local compatibility="${3:-none}"
  local app_client="${4:-none}"
  local promotion_contract="${5:-unchecked}"
  local migration="${6:-unchecked}"
  local work="${7:-PR 검증 스크립트 추가}"
  local reason="${8:-자동 검증 누락 방지}"
  local verification="${9:-bash scripts/test-harness-checks.sh}"
  local operational_impact="${10:-없음}"
  local review_point="${11:-스크립트 실패 조건}"
  local contract_indent="${12:-}"
  local migration_checked=" "
  local deployment_checked=" "
  local expand_checked=" "

  if [[ "$migration" == "expand" ]]; then
    migration_checked="x"
    deployment_checked="x"
    expand_checked="x"
  elif [[ "$migration" == "no_type" ]]; then
    migration_checked="x"
    deployment_checked="x"
  fi

  {
    printf '## 작업 내용\n\n- %s\n\n' "$work"

    if [[ "$target" != "missing" ]]; then
      printf '## 대상 브랜치\n\n'
      printf -- '- [%s] 일반 작업: `dev` 대상 PR\n' "$(mark "$target" "dev")"
      printf -- '- [%s] 운영 배포 승격: `dev` -> `main`\n' "$(mark "$target" "promotion")"
      printf -- '- [%s] 운영 긴급 수정: `hotfix/*` -> `main`, 배포 후 `dev` 반영 계획 작성\n\n' "$(mark "$target" "hotfix")"
    fi

    printf '## 변경 이유\n\n- %s\n\n' "$reason"
    printf '## 검증\n\n- %s\n\n' "$verification"
    printf '## 운영 영향\n\n- %s\n\n' "$operational_impact"
    printf '## 계약 영향\n\n'
    printf '%s- [x] API 계약 영향 범위를 확인함\n' "$contract_indent"
    printf '%s- [%s] 하위 호환 영향 없음\n' "$contract_indent" "$(mark "$compatibility" "none")"
    printf '%s- [%s] 하위 호환 영향 있음 - 운영 영향 또는 리뷰 포인트에 앱 클라이언트 영향과 대응 계획을 적음\n' "$contract_indent" "$(mark "$compatibility" "impact")"
    printf '%s- [%s] 앱 클라이언트 조율 필요 없음\n' "$contract_indent" "$(mark "$app_client" "none")"
    printf '%s- [%s] 앱 클라이언트 조율 필요 - main 승격 전 조율 계획을 적음\n' "$contract_indent" "$(mark "$app_client" "needed")"
    printf '%s- [%s] 운영 배포 승격 전 API 계약 테스트 영향 확인함\n\n' "$contract_indent" "$(mark "$promotion_contract" "checked")"

    printf '## 마이그레이션 영향 (DB 변경 시에만)\n\n'
    printf -- '- [%s] DB 변경이 하위 호환인지 검토함\n' "$migration_checked"
    printf -- '- [%s] 코드-스키마 배포 순서와 롤백 영향을 PR에 적음\n' "$deployment_checked"
    if [[ "$migration" != "minimal" ]]; then
      printf -- '- [%s] expand\n' "$expand_checked"
      printf -- '- [ ] backfill\n'
      printf -- '- [ ] switch\n'
      printf -- '- [ ] contract\n'
      printf -- '- [ ] seed/reference data\n'
    fi

    printf '\n## 리뷰 포인트\n\n- %s\n' "$review_point"
  } >"$path"
}

test_sensitive_paths() {
  local paths_file="$TMP_ROOT/paths.txt"
  local repo="$TMP_ROOT/unicode-sensitive-repo"

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

  write_file "$paths_file" \
    "secrets/SECRET.PEM" \
    "secrets/prod.KEY"
  assert_fail sensitive_blocks_uppercase_extensions "$ROOT_DIR/scripts/check-sensitive-paths.sh" --paths-file "$paths_file"

  mkdir -p "$repo"
  (
    cd "$repo"
    git init -q
    printf 'secret' > "테스트.pem"
    git add "테스트.pem"
    assert_fail sensitive_blocks_unicode_tracked "$ROOT_DIR/scripts/check-sensitive-paths.sh" --tracked
    assert_fail sensitive_blocks_unicode_staged "$ROOT_DIR/scripts/check-sensitive-paths.sh" --staged
  )
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

  assert_fail commit_message_rejects_blank_title \
    "$ROOT_DIR/scripts/check-commit-message.sh" --subject-only --subject "feat:    "
  assert_fail commit_message_rejects_trailing_period_with_space \
    "$ROOT_DIR/scripts/check-commit-message.sh" --subject-only --subject "feat: 제목. "

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
  write_pr_body "$body" dev none none unchecked unchecked
  assert_pass pr_contract_non_db "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"

  assert_fail pr_contract_target_checkbox_must_match_actual_base "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "main"

  write_pr_body "$body" missing none none unchecked unchecked
  assert_fail pr_contract_requires_target_branch_decision "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"

  write_pr_body "$body" dev unchecked none unchecked unchecked
  assert_fail pr_contract_requires_api_compatibility_decision "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"

  write_pr_body "$body" dev none unchecked unchecked unchecked
  assert_fail pr_contract_requires_app_client_coordination_decision "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"

  write_pr_body "$body" promotion none none unchecked unchecked \
    "dev 변경을 운영으로 승격" "운영 배포" "bash scripts/test-harness-checks.sh" \
    "API 계약 유지" "main 승격 검증"
  assert_fail pr_contract_promotion_requires_api_contract_test_check "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 운영 배포 승격" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "main"

  write_pr_body "$body" promotion none none checked unchecked \
    "dev 변경을 운영으로 승격" "운영 배포" "bash scripts/test-harness-checks.sh" \
    "API 계약 유지" "main 승격 검증"
  assert_pass pr_contract_promotion_with_contract_test_check "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 운영 배포 승격" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "main"

  write_file "$changed_files" \
    "A	src/main/resources/db/migration/V3__add_index.sql"
  write_pr_body "$body" dev none none unchecked unchecked
  assert_fail pr_contract_db_requires_checked_migration "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"

  write_pr_body "$body" dev none none unchecked expand \
    "DB migration 추가" "조회 성능 보완" "bash scripts/test-harness-checks.sh" \
    "migration 적용 필요" "migration 배포 순서"
  assert_pass pr_contract_db_checked "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"

  write_pr_body "$body" dev none none unchecked no_type \
    "DB migration 추가" "조회 성능 보완" "bash scripts/test-harness-checks.sh" \
    "migration 적용 필요" "migration 배포 순서"
  assert_fail pr_contract_db_requires_change_type "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"

  write_file "$changed_files" \
    "M	src/main/resources/db/migration/V1__init.sql"
  assert_fail pr_contract_db_blocks_existing_migration_modification "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"

  write_file "$changed_files" \
    "scripts/check-pr-contract.sh"
  write_pr_body "$body" dev none none unchecked minimal \
    "PR 검증 스크립트 추가" "자동 검증 누락 방지" "bash scripts/test-harness-checks.sh" \
    "없음" "스크립트 실패 조건" "  "
  assert_pass pr_contract_allows_indented_checkbox "$ROOT_DIR/scripts/check-pr-contract.sh" \
    --title "chore: 하네스 검사 추가" \
    --body-file "$body" \
    --changed-files "$changed_files" \
    --base-ref "dev"
}

test_migration_files() {
  local migration_dir="$TMP_ROOT/migration"

  if grep -q "sort -z" "$ROOT_DIR/scripts/check-migration-files.sh"; then
    fail "check-migration-files must not depend on GNU sort -z"
  fi

  mkdir -p "$migration_dir"
  write_file "$migration_dir/V1__init.sql" "select 1;"
  write_file "$migration_dir/V2__add_user_index.sql" "select 1;"
  assert_pass migration_files_good "$ROOT_DIR/scripts/check-migration-files.sh" --directory "$migration_dir"

  rm -rf "$migration_dir"
  mkdir -p "$migration_dir"
  write_file "$migration_dir/V1_init.sql" "select 1;"
  assert_fail migration_files_bad_name "$ROOT_DIR/scripts/check-migration-files.sh" --directory "$migration_dir"

  rm -rf "$migration_dir"
  mkdir -p "$migration_dir"
  write_file "$migration_dir/V1__init.sql" "select 1;"
  write_file "$migration_dir/V1__duplicate.sql" "select 1;"
  assert_fail migration_files_duplicate_version "$ROOT_DIR/scripts/check-migration-files.sh" --directory "$migration_dir"
}

test_install_git_hooks() {
  local hook="$ROOT_DIR/.githooks/commit-msg"

  chmod -x "$hook"
  assert_pass install_git_hooks_repairs_commit_msg_mode \
    env DALRYEO_INSTALL_HOOKS_SKIP_GIT_CONFIG=1 "$ROOT_DIR/scripts/install-git-hooks.sh"
  [[ -x "$hook" ]] || fail "install-git-hooks did not make commit-msg executable"
}

test_sensitive_paths
test_commit_message
test_pr_contract
test_migration_files
test_install_git_hooks

echo "test-harness-checks: OK"

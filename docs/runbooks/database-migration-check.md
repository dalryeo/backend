# Database Migration Check Runbook

- Status: Draft
- Audience: Engineers, Operators, Codex
- Source of Truth: No
- Last Reviewed: 2026-07-03

## 현재 상태

이 문서는 DB migration 배포 전후 확인 절차를 정리하기 위한 draft다. 운영 DB 접속, 백업, Azure 배포 권한, 복구 절차가 아직 이 문서에서 확인되지 않았으므로 Source of Truth로 사용하지 않는다.

작성 규칙은 `docs/standards/database-migration-policy.md`를 따른다. Flyway 채택 결정은 `docs/decisions/flyway-migration-system.md`를 확인한다.

## 백엔드가 PR에서 확인할 것

DB migration PR에서는 아래를 확인한다.

1. `src/main/resources/db/migration/` 아래 새 `V<number>__description.sql` 파일을 추가했는지 확인한다.
2. 대상 브랜치에 이미 커밋됐거나 이미 적용된 migration 파일을 수정, 삭제, rename하지 않았는지 확인한다.
3. PR 본문에 DB 변경 유형을 체크한다.
4. PR 본문에 코드-스키마 배포 순서와 rollback 또는 forward fix 판단을 적는다.
5. Entity 변경, repository/query 변경, migration 변경, 관련 테스트가 같은 변경 범위에 있는지 확인한다.

정적 검증 명령:

```bash
bash scripts/check-migration-files.sh
bash scripts/test-harness-checks.sh
bash scripts/docs-lint.sh
```

DB 연결이 가능한 환경에서는 관련 integration test 또는 전체 회귀를 실행한다.

```bash
./scripts/test-local.sh
```

실행하지 못하면 DB, Docker/Testcontainers, Flyway validation 등 실패 원인을 PR 검증 섹션에 적는다.

## 배포 전 확인할 것

운영 배포 전에는 운영 권한자가 아래를 확인한다.

- 적용 대상 환경이 `dev`인지 `prod`인지 확인한다.
- 배포 대상 DB와 애플리케이션 배포 대상이 같은 환경인지 확인한다.
- `flyway_schema_history`에서 이미 적용된 migration version을 확인한다.
- 새 migration이 기존 version과 충돌하지 않는지 확인한다.
- 위험한 변경이면 `expand`, `backfill`, `switch`, `contract` 순서가 맞는지 확인한다.
- backfill 또는 contract 변경이면 복구 가능성과 사용자 영향 범위를 확인한다.

`flyway_schema_history` 확인 SQL 예시:

```sql
SELECT installed_rank, version, description, type, script, checksum, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank;
```

## 실패 시 판단

Checksum mismatch가 발생하면 기존 migration 파일을 고치지 않는다.

- 아직 어떤 환경에도 적용되지 않은 작업 브랜치 migration이면 파일을 고칠 수 있다.
- `dev` 또는 `main` 환경 DB에 적용된 migration이면 새 migration으로 forward fix한다.
- `flyway repair`는 운영 이력을 바꾸는 작업이므로 운영 권한자 승인 없이 실행하지 않는다.

Migration은 성공했지만 애플리케이션이 Hibernate validate에서 실패하면 코드와 schema가 불일치한 상태다.

- 배포를 중단한다.
- 실패한 Entity/table/column을 확인한다.
- 기존 migration을 수정하지 않고 새 migration 또는 코드 수정으로 맞춘다.

## 아직 확인되지 않은 운영 절차

아래 항목은 실제 운영 권한과 절차가 확인된 뒤 Source of Truth runbook으로 승격할 때 채운다.

- 운영 DB 백업/복구 절차
- Azure 배포 전후 확인 위치
- migration 전용 job 사용 여부
- 운영 smoke test 목록
- 장애 상황에서 사용자 영향 확인 절차

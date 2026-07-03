# Database Migration Policy

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-07-03

## 이 문서가 정하는 것

달려 백엔드의 DB schema 변경은 Flyway versioned migration으로 관리한다.

Flyway를 채택한 이유와 초기 도입 판단은 `docs/decisions/flyway-migration-system.md`를 우선 확인한다. 이 문서는 반복 작업에서 지킬 작성 규칙과 PR 검증 기준을 정한다.

## 기본 원칙

- DB schema 변경은 `src/main/resources/db/migration/` 아래 새 migration 파일로 반영한다.
- Hibernate `ddl-auto=validate`는 schema 생성 도구가 아니라 Entity와 DB schema 일치 여부를 확인하는 검증 도구다.
- 대상 브랜치에 이미 커밋됐거나 `dev` 또는 `main` 환경 DB에 적용된 migration 파일은 수정, 삭제, rename하지 않는다.
- 이미 적용된 schema를 바꿔야 하면 기존 파일을 고치지 않고 새 버전 migration을 추가한다.
- Entity 변경, migration 변경, 관련 테스트 변경은 같은 PR에서 다룬다. 여러 단계가 필요하면 PR 순서를 명시한다.
- 기준 데이터(seed/reference data)의 원본이 Flyway라면 값 변경도 migration으로 남긴다.

## 파일 이름과 버전

Migration 파일은 아래 형식을 사용한다.

```text
V<number>__<description>.sql
```

규칙:

- `<number>`는 1 이상의 정수다.
- `<description>`은 소문자 영문, 숫자, underscore만 사용한다.
- 같은 버전 번호를 두 번 사용하지 않는다.
- 파일명 예시는 `V3__add_user_status_index.sql`처럼 쓴다.
- 날짜형 버전, 대문자 확장자, 공백 포함 파일명은 사용하지 않는다.

`scripts/check-migration-files.sh`가 이 규칙을 검사한다.

## 변경 유형

DB migration PR은 아래 유형 중 하나 이상을 PR 본문에 표시한다.

| 유형 | 의미 | 예시 |
| --- | --- | --- |
| `expand` | 기존 코드와 하위 호환되는 schema 확장 | nullable column 추가, 새 index 추가, 새 table 추가 |
| `backfill` | 기존 데이터 보정 | 새 column 값 채우기, 기준 데이터 보정 |
| `switch` | 애플리케이션이 새 schema를 사용하도록 전환 | 읽기/쓰기 경로를 새 column으로 변경 |
| `contract` | 더 이상 쓰지 않는 schema 제거 또는 제약 강화 | legacy column drop, nullable column을 not null로 변경 |
| `seed/reference data` | 기준 데이터 추가/수정 | tier metadata 값 변경 |

작은 변경은 하나의 PR에 여러 유형이 함께 들어갈 수 있다. 되돌리기 어렵거나 운영 영향이 큰 변경은 `expand -> backfill -> switch -> contract` 순서로 나눈다.

## 하위 호환 기준

`dev`와 `main`을 나눠 운영하므로 migration은 배포 순서와 호환성을 기준으로 판단한다.

- 대상 브랜치에 이미 커밋됐거나 `dev`에 이미 적용된 migration은 수정하지 않는다.
- `main` 승격 PR에는 운영 DB 적용 순서와 rollback 또는 forward fix 판단을 적는다.
- `expand`는 이전 애플리케이션 코드가 떠 있어도 깨지지 않아야 한다.
- `backfill`은 재실행 가능하거나 실패 후 재개 기준을 설명해야 한다.
- `switch`는 새 schema가 적용된 뒤 배포되어야 한다.
- `contract`는 새 코드가 충분히 검증되고 legacy 경로가 더 이상 필요 없을 때 마지막에 처리한다.

## 위험한 변경 기준

아래 변경은 단일 PR로 크게 묶지 않는다.

- 기존 column 삭제
- 기존 column type 변경
- not null 제약 추가
- unique 제약 추가
- 대량 데이터 backfill
- table rename 또는 column rename
- 애플리케이션 코드가 동시에 읽고 쓰는 핵심 table 변경

이런 변경은 가능한 한 `expand`, `backfill`, `switch`, `contract` 단계로 나누고, 각 PR의 운영 영향에 배포 순서와 실패 시 조치를 적는다.

## PR 검증 기준

DB migration 파일이 바뀌는 PR은 `.github/PULL_REQUEST_TEMPLATE.md`의 마이그레이션 영향 섹션을 채운다.

CI의 `scripts/check-pr-contract.sh`는 아래를 검사한다.

- DB 변경 하위 호환 검토 체크 여부
- 코드-스키마 배포 순서와 rollback 영향 작성 체크 여부
- migration 변경 유형 체크 여부
- 대상 브랜치에 이미 커밋된 migration 파일 수정, 삭제, rename 여부

CI의 `scripts/check-migration-files.sh`는 아래를 검사한다.

- migration 파일명 형식
- migration 버전 중복

## 검증 명령

문서와 정적 규칙 변경은 아래를 실행한다.

```bash
bash scripts/test-harness-checks.sh
bash scripts/check-migration-files.sh
bash scripts/docs-lint.sh
```

DB schema나 Entity가 바뀌면 관련 테스트와 `./scripts/test-local.sh`를 실행한다. 테스트 DB 또는 Docker/Testcontainers 문제로 전체 회귀를 실행하지 못하면, 실패 원인과 실행한 부분 검증을 PR에 적는다.

## 운영 DB 직접 변경

운영 DB에 직접 SQL을 실행하는 것은 예외다. 필요한 경우 PR 또는 운영 기록에 아래를 남긴다.

- 실행 대상 환경
- 실행 SQL 또는 migration과의 관계
- 실행 전 백업/복구 가능성
- 실행 후 확인할 query
- 애플리케이션 배포 순서

수동 SQL로 운영 상태를 바꾼 뒤 Flyway 이력과 어긋나게 두면 안 된다. 가능한 한 후속 migration으로 이력을 코드에 남긴다.

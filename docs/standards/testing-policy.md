# Testing Policy

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-07-03

## 이 문서가 정하는 것

달려 백엔드의 로컬 검증, PR 검증, 완료 보고 기준을 정한다.

## PR 검증 기준

GitHub Actions의 `Backend Verify` workflow는 `pull_request` 이벤트에서 실행한다. 대상 브랜치를 제한하지 않으므로 `dev` 대상 PR과 `main` 대상 PR은 같은 검증을 받아야 한다.

PR에서는 아래 검증이 실행된다.

- `scripts/test-harness-checks.sh`
- `scripts/docs-lint.sh`
- `scripts/check-migration-files.sh`
- `scripts/check-sensitive-paths.sh --tracked`
- `scripts/check-pr-contract.sh --changed-files changed-files.txt --base-ref "$GITHUB_BASE_REF"`
- `./scripts/test-local.sh`

`changed-files.txt`는 `git diff --name-status`로 만든다. DB migration PR에서 기존 migration 파일 수정, 삭제, rename을 구분하기 위해 파일 상태가 필요하다.

## 로컬 검증 기준

변경 범위에 맞는 가장 좁은 검증을 먼저 실행하고, 완료 전에는 영향 범위에 맞게 넓힌다.

문서와 하네스 변경:

```bash
bash scripts/test-harness-checks.sh
bash scripts/docs-lint.sh
```

DB migration 규칙 변경:

```bash
bash scripts/check-migration-files.sh
bash scripts/test-harness-checks.sh
```

코드 동작 변경:

```bash
./scripts/test-local.sh
```

특정 테스트만 필요한 경우:

```bash
./scripts/test-local.sh --tests <테스트 클래스명>
```

Gradle 직접 실행이 필요한 경우에는 `--no-daemon`을 사용한다.

```bash
./gradlew --no-daemon test --tests <테스트 클래스명>
```

## dev/prod 설정 혼동 방지

Profile 또는 배포 workflow를 바꿀 때는 dev/prod 값이 섞이지 않는지 확인한다.

- dev 배포 workflow는 `dev` 브랜치 push에 반응한다.
- dev 배포 workflow는 `SPRING_PROFILES_ACTIVE=dev`를 사용한다.
- dev 배포 workflow는 `SENTRY_ENVIRONMENT=dev`를 사용한다.
- prod profile은 Swagger를 닫고 `SENTRY_ENVIRONMENT` 기본값을 `prod`로 둔다.
- dev profile은 Swagger를 열되 에러 상세와 SQL 로그를 노출하지 않는다.

이 계약은 `src/test/java/com/ohgiraffers/dalryeo/config/CiWorkflowContractTest.java`, `SentryProfilePropertiesTest`, `SwaggerProfilePropertiesTest`, `ProfileLoggingPropertiesTest`에서 확인한다.

## 완료 보고 기준

작업 보고에는 실행한 검증 명령과 결과를 적는다.

검증을 실행하지 못했거나 실패했다면 아래를 함께 적는다.

- 실패한 명령
- 실패 원인
- 이번 변경과 직접 관련 있는지 판단
- 대신 실행한 부분 검증

전체 회귀 테스트가 DB, Docker/Testcontainers, Flyway validation 같은 환경 문제로 실패하면 실패 사실을 숨기지 않고 PR 검증 섹션에 남긴다.

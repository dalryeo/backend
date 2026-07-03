# Git Branch Strategy

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-07-03

## 현재 기준

달려 백엔드는 `main`과 `dev` 두 개의 장기 브랜치를 둔다.

- `main`은 실제 운영 중인 앱 코드 기준이다.
- `dev`는 TestFlight처럼 개발 검증 환경에 배포되는 코드 기준이다.
- 모든 작업은 짧은 작업 브랜치에서 진행하고 PR로 반영한다.
- 일반 기능과 버그 수정은 `dev`로 먼저 머지한다.
- 운영 배포는 검증된 `dev` 변경을 `main`으로 승격하는 PR로 처리한다.
- 운영 긴급 수정은 `main`에서 `hotfix/*` 브랜치를 만들고, 배포 후 `dev`로 되돌려 반영한다.
- 배포 환경 분리는 브랜치 이름만으로 끝내지 않고 workflow, Dockerfile, Spring profile, 환경 변수, 외부 리소스로 함께 처리한다.

## 장기 브랜치 역할

| 브랜치 | 역할 | 배포 대상 | 머지 기준 |
| --- | --- | --- | --- |
| `main` | 운영 코드 | production | 운영 배포 가능한 변경만 PR로 반영 |
| `dev` | 개발 검증 코드 | development | 기능, 버그 수정, 운영 전 검증 변경을 PR로 반영 |

`main`과 `dev`에는 직접 push하지 않는다. 혼자 작업하더라도 PR과 CI를 통해 변경 단위, 검증 결과, 운영 영향을 남긴다.

운영 배포에서 GitHub Environment의 required reviewer는 필수로 두지 않는다. 1인 운영에서는 PR 기록과 CI 통과를 기본 안전장치로 삼고, 수동 승인자는 필요해질 때 다시 검토한다.

## 확인된 저장소 설정

2026-07-03 기준 GitHub API와 checked-in workflow로 확인한 상태는 아래와 같다.

- GitHub API에서 `main`은 `protected: true`다. 이 제한은 원격 push 단계에서 적용되는 서버 정책이다.
- 로컬 git hook은 커밋 메시지와 staged 민감 경로만 검사한다. `main` 로컬 commit 자체를 막지는 않는다.
- 원격 저장소에는 `dev` 브랜치가 있다. 현재 `dev`는 `protected: false`이므로, 공유 개발 브랜치로 사용하기 전에 직접 push 금지 보호 규칙을 적용한다.
- GitHub repository ruleset은 없다.
- GitHub Environment는 `dev`, `copilot`이 있고, 두 environment 모두 required reviewer/protection rule은 없다.
- `.github/workflows/deploy-dev.yml`은 `dev` push에 반응한다.
- `.github/workflows/deploy-dev.yml`은 `SPRING_PROFILES_ACTIVE=dev`, `SENTRY_ENVIRONMENT=dev`를 설정한다.

## 기본 흐름

```text
feat/*, fix/*, refactor/* -> PR -> dev -> 개발 환경 배포
dev 검증 완료 -> PR -> main -> 운영 환경 배포
hotfix/* from main -> PR -> main -> 운영 환경 배포 -> dev로 반영
```

`dev`에서 검증이 끝난 변경만 `main`으로 보낸다. `main`에 먼저 들어간 hotfix는 운영 반영 후 `dev`에 back-merge하거나 동일 수정 PR을 열어 두 브랜치가 다시 수렴하게 한다.

## 브랜치 이름 규칙

브랜치는 아래 형식을 사용한다.

| 작업 성격 | 형식 | 예시 |
| --- | --- | --- |
| 기능 추가 | `feat/<area>-<topic>` | `feat/auth-refresh-token-hash` |
| 버그 수정 | `fix/<area>-<topic>` | `fix/record-weekly-summary-null` |
| 리팩터링 | `refactor/<area>-<topic>` | `refactor/tier-score-calculation` |
| 문서 변경 | `docs/<topic>` | `docs/repository-harness` |
| 설정/빌드/운영성 | `chore/<topic>` | `chore/gradle-postgres-profile` |
| 운영 긴급 수정 | `hotfix/<area>-<topic>` | `hotfix/auth-token-expiration` |

`area`는 현재 코드 모듈이나 운영 주제에 맞춰 짧게 쓴다.

- `auth`
- `onboarding`
- `record`
- `analysis`
- `ranking`
- `weeklytier`
- `tier`
- `db`
- `infra`

문서 작업은 보통 area를 붙이지 않고 `docs/<topic>`을 쓴다.

## DB 변경 브랜치

DB 변경은 일반 기능보다 더 작게 나눈다.

- `expand`, `backfill`, `switch`, `contract`는 가능하면 별도 PR로 나눈다.
- `contract`는 운영 검증이 끝난 뒤 마지막에 처리한다.
- 애플리케이션 코드와 SQL 문서가 함께 필요하면 같은 PR에서 다루되, 되돌리기 어려운 DDL은 크게 묶지 않는다.
- `dev` 환경에 이미 적용된 migration은 수정하지 않고 새 migration으로 보정한다.
- `main` 승격 PR에는 운영 DB 적용 순서와 롤백 영향을 적는다.

예시:

- `feat/db-expand-auth-tables`
- `feat/db-backfill-oauth-client`
- `refactor/db-switch-running-source`
- `chore/db-contract-legacy-user-columns`

## PR 운영 규칙

- `main`과 `dev`에는 직접 push하지 않는다.
- 작업 브랜치는 가능하면 1~3일 안에 머지한다.
- PR은 한 가지 목적만 담는다.
- 큰 변경은 여러 PR로 쪼갠다.
- 작업 브랜치는 대상 브랜치 기준 충돌을 먼저 정리한다.
- 머지는 기본적으로 Squash merge를 사용한다.
- PR 본문은 `.github/PULL_REQUEST_TEMPLATE.md`를 따른다.
- 운영 배포 시점은 태그로 남긴다.

## PR 대상 브랜치 기준

- 일반 기능, 버그 수정, 리팩터링, 테스트 보강: `dev` 대상 PR
- 운영 배포 승격: `dev`에서 `main` 대상 PR
- 운영 긴급 수정: `main`에서 만든 `hotfix/*`를 `main` 대상 PR로 반영
- hotfix 후 동기화: `main` 변경을 `dev`에 back-merge하거나 별도 PR로 반영

## 백엔드 변경 가능 범위

백엔드 작업자는 아래 범위를 직접 변경할 수 있다. 단, 비밀 값은 열거나 커밋하지 않는다.

- 브랜치 전략, 커밋 규칙, PR 기준 같은 `docs/standards` 문서
- API 계약, 도메인 규칙, DB migration, rollback 판단 등 백엔드 동작에 직접 연결되는 문서
- Spring profile별 코드 경로와 안전한 기본값
- 환경 변수 이름을 읽는 애플리케이션 코드와 checked-in 설정 파일의 비밀 없는 기본값
- 테스트, 검증 스크립트, PR contract check 같은 백엔드 품질 게이트
- Dockerfile의 애플리케이션 빌드와 실행 방식

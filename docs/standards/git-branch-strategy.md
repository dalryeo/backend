# Git Branch Strategy

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-15

## 현재 기준

달려 백엔드는 `main` 하나만 장기 유지하는 짧은 브랜치 전략을 쓴다.

- 장기 브랜치는 `main`만 둔다.
- `develop`, `release/*`, `hotfix/*`는 현재 사용하지 않는다.
- 모든 작업은 짧은 작업 브랜치에서 진행하고 PR로 `main`에 반영한다.
- 배포 환경 분리는 브랜치가 아니라 workflow, Dockerfile, 환경 설정으로 처리한다.

## 브랜치 이름 규칙

브랜치는 아래 형식을 사용한다.

| 작업 성격 | 형식 | 예시 |
| --- | --- | --- |
| 기능 추가 | `feat/<area>-<topic>` | `feat/auth-refresh-token-hash` |
| 버그 수정 | `fix/<area>-<topic>` | `fix/record-weekly-summary-null` |
| 리팩터링 | `refactor/<area>-<topic>` | `refactor/tier-score-calculation` |
| 문서 변경 | `docs/<topic>` | `docs/repository-harness` |
| 설정/빌드/운영성 | `chore/<topic>` | `chore/gradle-postgres-profile` |

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

예시:

- `feat/db-expand-auth-tables`
- `feat/db-backfill-oauth-client`
- `refactor/db-switch-running-source`
- `chore/db-contract-legacy-user-columns`

## PR 운영 규칙

- `main`에는 직접 push하지 않는다.
- 작업 브랜치는 가능하면 1~3일 안에 머지한다.
- PR은 한 가지 목적만 담는다.
- 큰 변경은 여러 PR로 쪼갠다.
- 머지 전에는 최신 `main` 기준 충돌을 먼저 정리한다.
- 머지는 기본적으로 Squash merge를 사용한다.
- PR 본문은 `.github/PULL_REQUEST_TEMPLATE.md`를 따른다.
- 배포 시점은 브랜치가 아니라 태그로 남긴다.

## 지금 사용하지 않는 전략

현재 단계에서는 아래 전략을 쓰지 않는다.

- Git Flow: `main`, `develop`, `release`, `hotfix`를 모두 운영하는 방식
- 환경별 브랜치 분리
- 장기간 살아있는 기능 브랜치
- 팀원별 개인 영구 브랜치

이 방식들은 릴리즈 절차가 복잡한 조직에는 맞을 수 있지만, 현재 레포 규모와 변경 속도에는 과하다.

## release 브랜치를 다시 검토할 때

아래 상황이 생기면 `release/*` 도입을 다시 검토한다.

- 모바일 앱과 백엔드 릴리즈 타이밍을 분리해야 할 때
- 운영 배포 전 코드 프리즈 기간이 필요할 때
- 여러 팀이 같은 레포에서 오래 병렬 개발할 때
- API 버전을 동시에 두 개 이상 유지해야 할 때

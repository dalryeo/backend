# Testing Policy

- Status: Draft
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-22

## 현재 상태

이 문서는 아직 확정된 테스트 기준 문서가 아니다. 현재 테스트 실행 기준은 `AGENTS.md`의 테스트와 검증 섹션, 루트의 `scripts/test-local.sh`, Gradle 설정, 실제 테스트 코드를 우선 확인한다.

이 문서는 나중에 달려 백엔드의 로컬/에이전트 테스트 실행 기준을 반복 적용 가능한 standard로 정리하기 위해 만든 자리다.

## 나중에 작성할 내용

- `scripts/test-local.sh` 기본 사용법
- `.env.test`와 실제 DB 접속 정보 관리 기준
- 특정 테스트만 실행할 때의 Gradle 옵션 전달 방식
- 단위 테스트와 통합 테스트를 선택하는 기준
- API 계약, 예외 응답, 영속성, 트랜잭션, outbox 영향 검증 기준
- 전체 회귀 테스트를 실행해야 하는 변경 범위
- 완료 보고에 포함할 실행/미실행 검증 구분 기준

## 작성할 때 확인할 파일

- `scripts/test-local.sh`
- `build.gradle`
- `src/test/`
- `.github/workflows/`
- `src/main/resources/application.yml`
- `src/test/resources/application-test.yml`

## 완료 조건

이 문서를 `Source of Truth: Yes`로 바꾸려면 실제 실행 가능한 명령, 테스트 선택 기준, 실패 시 다음 확인 지점이 현재 프로젝트 설정과 맞아야 한다. 확인되지 않은 테스트 절차를 standard로 확정하지 않는다.

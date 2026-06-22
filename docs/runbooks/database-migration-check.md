# Database Migration Check Runbook

- Status: Draft
- Audience: Engineers, Operators, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-16

## 현재 상태

이 문서는 아직 운영 절차 문서가 아니다. Flyway 채택 결정은 `docs/decisions/flyway-migration-system.md`를 우선 확인한다.

이 문서는 나중에 DB migration을 배포하거나 실패를 확인할 때 순서대로 따라 할 절차를 정리하기 위해 만든 자리다.

## 나중에 작성할 내용

아래 항목을 실제 배포 workflow와 운영 DB 접근 방식을 확인한 뒤 채운다.

- 배포 전 확인할 migration 파일 목록
- 배포 전 확인할 DB 접속 대상과 환경 변수
- 배포 전 백업 또는 복구 가능성 확인 기준
- 배포 중 확인할 Flyway 로그
- 배포 후 확인할 `flyway_schema_history` 조회 방법
- 배포 후 Hibernate validate 실패 여부 확인 방법
- 배포 후 핵심 API smoke test 목록
- checksum mismatch가 발생했을 때의 판단 순서
- 이미 존재하는 table, constraint, index와 충돌했을 때의 판단 순서
- migration은 성공했지만 애플리케이션 기동이 실패했을 때의 판단 순서
- 운영에 적용된 migration을 되돌릴 수 없는 경우 forward fix를 선택하는 기준
- 장애 상황에서 사용자에게 영향이 있는지 확인하는 방법

## 작성할 때 확인할 파일

- 실제 배포 workflow
- `Dockerfile`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/`
- 운영 DB 접속과 secret 관리 방식
- 로컬 검증 스크립트

## 완료 조건

이 문서를 `Source of Truth: Yes`로 바꾸려면 명령, 확인 SQL, 실패 시 다음 조치가 실제 운영 방식과 맞아야 한다. 확인되지 않은 절차를 runbook으로 확정하지 않는다.

# Database Migration Policy

- Status: Draft
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-16

## 현재 상태

이 문서는 아직 확정된 기준 문서가 아니다. Flyway 채택 결정은 `docs/decisions/flyway-migration-system.md`를 우선 확인한다.

이 문서는 나중에 DB schema 변경을 반복해서 다룰 때 지켜야 하는 규칙을 정리하기 위해 만든 자리다.

## 나중에 작성할 내용

아래 항목을 코드와 운영 기준을 확인한 뒤 채운다.

- migration 파일 이름과 버전 부여 규칙
- 운영에 적용된 migration 파일을 수정하지 않는 기준
- Entity 변경, migration 변경, 테스트 변경을 같은 작업 범위에서 맞추는 기준
- `ddl-auto=validate`와 Flyway의 역할 구분
- 빈 DB 검증과 기존 DB 변경 검증을 나누는 기준
- table, column, constraint, index 추가 시 확인할 항목
- 데이터 backfill이 필요한 변경과 필요 없는 변경을 구분하는 기준
- expand, backfill, switch, contract 방식이 필요한 경우
- rollback 대신 forward migration을 선택해야 하는 경우
- seed data나 기준 데이터 변경을 migration에 넣을지 판단하는 기준
- 운영 DB에 직접 SQL을 실행해야 하는 예외 상황과 승인 기준

## 작성할 때 확인할 파일

- `build.gradle`
- `src/main/resources/application.yml`
- `src/main/resources/db/migration/`
- DB 관련 Entity
- DB 변경과 연결된 integration test
- 실제 배포 workflow

## 완료 조건

이 문서를 `Source of Truth: Yes`로 바꾸려면 실제 migration 작성 기준과 검증 명령이 확정되어야 한다. 단순히 Flyway를 사용한다는 사실만으로는 이 문서를 기준 문서로 보지 않는다.

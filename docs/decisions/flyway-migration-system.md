# Flyway Migration System

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-16

## 결정

달려 백엔드는 PostgreSQL 스키마 변경 이력을 Flyway migration 파일로 관리한다. 애플리케이션은 Hibernate `ddl-auto=validate`를 사용하고, 스키마 생성과 변경은 `src/main/resources/db/migration/` 아래의 버전 파일을 기준으로 추적한다.

## 배경

Flyway 도입 전에는 애플리케이션이 PostgreSQL과 `ddl-auto=validate`를 기준으로 동작했지만, 커밋 가능한 DB migration 경로가 없었다. 일부 SQL은 `docs/` 아래 작업 메모로 존재했지만, 운영 배포와 빈 DB 재현의 기준으로 삼기에는 약했다.

이 상태에서는 새 환경에서 DB를 재현하거나, Entity와 실제 스키마의 차이를 검증하거나, 운영 DB 변경 이력을 추적하기 어렵다.

## 선택한 방식

Spring Boot 기본 Flyway 경로를 사용한다.

```text
src/main/resources/db/migration/
```

초기 도입 시점에는 `V1__init.sql`로 현재 애플리케이션이 필요한 초기 스키마를 만들었다. 이후 DB 구조 변경은 기존 migration을 수정하지 않고 새 버전 migration으로 추가한다.

현재 실행 기준은 아래 파일이다.

- `build.gradle`: `org.flywaydb:flyway-core` 의존성
- `src/main/resources/application.yml`: Hibernate `ddl-auto=validate`
- `src/main/resources/db/migration/`: Flyway versioned migration

## 주요 판단

기존 docs SQL을 그대로 복사하지 않고, 현재 Entity와 운영 흐름에 필요한 테이블, 제약, 인덱스를 기준으로 초기 migration을 작성했다.

특히 아래 판단이 포함됐다.

- 러닝 기록 저장 이후 주간 집계에 필요한 `weekly_user_stats`를 migration에 포함한다.
- 기록 저장 후 집계 갱신 흐름에 필요한 `record_outbox_events`를 migration에 포함한다.
- 코드 validator와 DB check constraint가 어긋나지 않도록 `running_records.source` 허용 값을 맞춘다.
- 현재 제품 범위에서는 사용자당 refresh token 하나 정책을 유지한다.
- 현재 outbox는 범용 outbox가 아니라 running record 기반 주간 집계 갱신용 outbox로 본다.

향후 멀티 디바이스 refresh token이나 범용 outbox가 필요해지면 새 migration과 애플리케이션 로직 변경을 함께 다룬다.

## 운영 영향

Flyway 도입 이후에는 운영에 적용된 migration 파일을 수정하지 않는다. 이미 적용된 스키마를 바꿔야 하면 새 버전 migration을 추가한다.

운영 DB에 수동으로 만든 기존 객체가 있으면 Flyway migration과 충돌할 수 있다. 배포 전에는 적용 대상 DB의 기존 객체와 `flyway_schema_history` 상태를 확인해야 한다.

## 검증 기준

Flyway migration 변경은 최소한 아래 흐름을 확인해야 한다.

- 빈 PostgreSQL DB에서 migration이 성공한다.
- migration 후 Hibernate `ddl-auto=validate`가 통과한다.
- 변경된 테이블, 제약, 인덱스와 직접 연결된 테스트가 통과한다.
- 운영에 이미 적용된 migration을 수정하지 않았는지 확인한다.

상세 작성 규칙은 추후 `docs/standards/database-migration-policy.md`에 정리한다. 배포 전후 확인 절차는 추후 `docs/runbooks/database-migration-check.md`에 정리한다.

## 관련 이력

- 2026-05-12: Flyway 의존성과 초기 migration 도입
- 2026-05-13: 운영 DB에 Flyway schema history와 migration 결과 확인

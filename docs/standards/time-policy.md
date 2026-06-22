# Time Policy

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-20

## 이 문서가 정하는 것

달려 백엔드에서 서비스 날짜, 주차, 월 기준을 계산할 때 따를 시간 기준을 정한다.

러닝 기록 저장 API의 시간 요청 계약은 `docs/decisions/running-record-time-contract.md`를 함께 본다.

## 기준 시간대

현재 서비스 기준 시간대는 `Asia/Seoul`이다.

설정 기준은 아래 값이다.

```text
app.time-zone
```

기본값은 `Asia/Seoul`이며, 하위 호환을 위해 `WEEKLY_TIER_ZONE`, `WEEKLY_TIER_FINALIZATION_ZONE` 환경 변수도 설정 fallback에 포함돼 있다.

## 날짜 계산 규칙

- 서비스의 “오늘”은 `app.time-zone` 기준 날짜다.
- 주간 기준일은 월요일이다.
- 현재 주 시작일은 서비스 기준 날짜에서 이전 또는 같은 월요일이다.
- 월간 기준일은 서비스 기준 날짜의 1일이다.

서비스 날짜와 주차 계산은 `ServiceDateProvider`를 우선 사용한다.

도메인 로직에서 JVM 기본 시간대나 서버 로컬 날짜에 의존해 `LocalDate.now()`를 직접 호출하면 안 된다. 특히 record, ranking, weekly tier, analysis처럼 주차와 집계가 연결되는 코드는 같은 provider를 써야 한다.

## 기록 시간 규칙

`POST /records`의 `startAt`, `endAt`은 offset이 포함된 `OffsetDateTime`으로 받는다.

저장할 때는 요청 시각을 `app.time-zone` 기준 `LocalDateTime`으로 변환한다. 현재 DB의 `running_records.start_at`, `running_records.end_at`은 `timestamp` 컬럼이므로 offset 자체를 보존하지 않는다.

미래 기록 검증처럼 실제 시각 비교가 필요한 로직은 `Instant` 기준으로 판단한다.

## 변경 기준

시간 정책을 바꾸는 변경은 아래 영향을 함께 확인해야 한다.

- 러닝 기록 저장 검증
- 주간 요약
- 주간 기록 목록
- 랭킹 목록과 내 랭킹
- 주간 티어 확정
- API 계약 테스트

서비스 시간대, 주 시작일, DB timestamp 해석을 바꾸는 작업은 문서만 수정하지 말고 구현, 테스트, migration 필요 여부를 같은 변경 범위에서 판단한다.

## 테스트 기준

시간 경계가 중요한 테스트는 고정된 시간 또는 mock provider를 사용한다.

최소한 아래 경계를 검증한다.

- UTC 일요일 15:00은 KST 월요일 00:00이다.
- UTC 일요일 14:59:59는 KST 일요일 23:59:59이다.
- 위 두 시점에서 current week start가 KST 기준으로 달라져야 한다.

## 구현 앵커

- `src/main/resources/application.yml`
- `src/main/java/com/ohgiraffers/dalryeo/common/time/ServiceDateProvider.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/service/RecordService.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/service/WeeklyUserStatsService.java`
- `src/main/java/com/ohgiraffers/dalryeo/ranking/service/RankingService.java`
- `src/main/java/com/ohgiraffers/dalryeo/weeklytier/service/WeeklyTierService.java`
- `src/main/java/com/ohgiraffers/dalryeo/weeklytier/scheduler/WeeklyTierFinalizationScheduler.java`
- `src/test/java/com/ohgiraffers/dalryeo/common/time/ServiceDateProviderTest.java`

## 보관된 원문

- `docs/archive/records/week-start-asia-seoul-plan.md`

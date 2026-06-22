# Weekly Ranking Read Model

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-20

## 결정

주간 랭킹은 요청마다 `running_records` 원본을 전체 집계하지 않고, `weekly_user_stats` read model을 기준으로 조회한다.

러닝 기록 저장은 원본 기록을 보존하고, 주간 집계 반영은 `record_outbox_events`를 통해 비동기로 처리한다. 랭킹 조회는 `weekly_user_stats`와 랭킹용 인덱스를 사용한다.

## 배경

주간 랭킹은 사용자에게 자주 노출되는 조회 API다. 원본 기록을 매번 집계하면 사용자와 기록 수가 늘어날수록 DB 비용과 애플리케이션으로 전달되는 row 수가 커진다.

초기 규모에서는 Redis ranking set이나 별도 캐시를 먼저 도입하지 않고, PostgreSQL read model과 인덱스로 단순하게 해결하는 쪽이 운영 비용이 낮다.

## 선택한 방식

`weekly_user_stats`는 사용자와 주차별로 하나의 row를 가진다.

주요 값은 아래와 같다.

- `run_count`
- `total_distance_km`
- `total_duration_sec`
- `weighted_pace_sum`
- `avg_pace_sec_per_km`
- `tier_score_sum`
- `tier_score`

러닝 기록이 저장되면 같은 트랜잭션에서 outbox 이벤트를 만든다. 별도 processor가 이벤트를 처리하면서 해당 주차의 `weekly_user_stats`를 upsert한다.

랭킹 조회는 아래 기준을 사용한다.

- 점수 랭킹: `tier_score DESC`, `total_distance_km DESC`, `user_id ASC`
- 거리 랭킹: `total_distance_km DESC`, `tier_score DESC`, `user_id ASC`
- 내 랭킹: 목록과 같은 정렬 조건으로 나보다 앞선 사용자 수를 계산한다.

## 주요 판단

- `running_records`는 원본 데이터다. 랭킹 조회 최적화를 위해 원본을 대체하지 않는다.
- `weekly_user_stats`는 반복 조회를 위한 read model이다.
- 저장 성공과 랭킹 반영은 같은 순간을 보장하지 않는다.
- outbox 지연이나 실패가 있으면 원본 기록과 주간 집계가 일시적으로 다를 수 있다.
- 캐시는 현재 기본 구조가 아니다. DB read model과 인덱스가 먼저다.
- Redis나 ranking snapshot은 트래픽과 운영 요구가 커졌을 때 별도 결정으로 다룬다.

## 운영 영향

랭킹 값이 이상하면 먼저 아래를 확인한다.

- `record_outbox_events`에 `PENDING`, `PROCESSING`, `FAILED` 이벤트가 쌓였는지
- `weekly_user_stats`에 사용자와 주차별 row가 생성됐는지
- 랭킹 목록 쿼리와 내 랭킹 count 쿼리가 같은 정렬 조건을 쓰는지
- `week_start_date`가 서비스 시간대 기준 주차와 일치하는지

outbox 재처리나 주간 집계 재계산 절차가 필요하면 별도 runbook으로 다룬다.

## 구현 앵커

- `src/main/resources/db/migration/V1__init.sql`
- `src/main/java/com/ohgiraffers/dalryeo/record/entity/WeeklyUserStats.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/service/WeeklyUserStatsService.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/repository/WeeklyUserStatsRepository.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/outbox/RecordOutboxEvent.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/outbox/RecordOutboxEventProcessor.java`
- `src/main/java/com/ohgiraffers/dalryeo/ranking/service/RankingService.java`
- `src/test/java/com/ohgiraffers/dalryeo/record/service/RecordAggregationIntegrationTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/ranking/service/RankingServiceTest.java`

## 보관된 원문

- `docs/archive/ranking/weekly-ranking-performance-improvement.md`
- `docs/archive/performance/weekly-ranking-explain-result.md`
- `docs/archive/performance/weekly-ranking-benchmark.sql`

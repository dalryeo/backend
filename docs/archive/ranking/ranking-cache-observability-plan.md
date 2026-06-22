# 랭킹과 주간 조회 성능 관측 및 캐싱 도입 기준

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

관련 이슈: #40

권장 브랜치: `perf/ranking-observability`

## 배경

현재 랭킹은 `weekly_user_stats` 집계 테이블과 랭킹 인덱스, limit 100 구조를 사용한다. DAU 수백~수천 규모에서는 Redis 같은 캐시를 바로 추가하기보다 PostgreSQL 인덱스와 집계 테이블이 실제로 버티는지 관측하는 편이 실용적이다.

다만 런칭 후 랭킹 API 호출이 많아지면 같은 주차 데이터를 반복 조회하게 된다. 캐싱을 도입할지 판단할 기준을 미리 정해두면 과도한 오버엔지니어링을 피하면서도 장애 징후를 빠르게 볼 수 있다.

## 목표

- 랭킹/주간 조회 API의 성능 기준을 정한다.
- DB 쿼리 플랜과 index 사용 여부를 확인한다.
- 캐싱 도입 조건을 수치로 정한다.
- 캐시를 도입하지 않는 동안 확인할 운영 지표를 정한다.

## 제외 범위

- Redis 도입
- 캐시 구현
- 랭킹 계산 로직 변경
- 집계 테이블 재설계

이번 작업은 기준 수립과 관측에 집중한다.

## 설계 방향

먼저 관측 기준을 정한다.

```text
대상 API:
- GET /ranking/weekly/score
- GET /ranking/weekly/distance
- GET /ranking/me
- GET /records/summary
- GET /weekly/summary/current
```

캐싱 도입 후보:

```text
weekly score ranking:
TTL 30~60초 가능

weekly distance ranking:
TTL 30~60초 가능

ranking/me:
개인화 응답이라 캐시 효율 낮음
DB count query 최적화 우선
```

초기 기준:

```text
p95 > 300ms 지속
DB CPU > 70% 지속
ranking query slow log 반복
같은 endpoint 호출량이 분당 수백 회 이상
```

위 조건이 충족되기 전에는 캐시 도입을 보류한다.

## 주요 확인 파일

- `src/main/java/com/ohgiraffers/dalryeo/ranking/service/RankingService.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/repository/WeeklyUserStatsRepository.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/entity/WeeklyUserStats.java`
- `docs/archive/performance/weekly-ranking-explain-result.md`
- `docs/archive/performance/weekly-ranking-benchmark.sql`

## 실행 체크리스트

- [ ] 현재 랭킹 쿼리의 EXPLAIN 결과를 확인한다.
- [ ] `idx_weekly_user_stats_score_ranking` 사용 여부를 확인한다.
- [ ] `idx_weekly_user_stats_distance_ranking` 사용 여부를 확인한다.
- [ ] `/ranking/me` count query 비용을 확인한다.
- [ ] API별 p95/p99 관측 방법을 정한다.
- [ ] 캐싱 도입 기준을 README 또는 운영 문서에 연결할지 결정한다.

## 검증 계획

코드 변경이 없으면 테스트보다 쿼리 분석이 중심이다.

```sql
EXPLAIN ANALYZE
SELECT s.*
FROM weekly_user_stats s
JOIN users u ON u.id = s.user_id
WHERE s.week_start_date = :weekStartDate
  AND s.run_count > 0
  AND u.status <> 'WITHDRAWN'
  AND u.nickname IS NOT NULL
ORDER BY s.tier_score DESC, s.total_distance_km DESC, s.user_id ASC
LIMIT 100;
```

코드 변경이 생기면 실행:

```bash
./gradlew test --tests '*RankingServiceTest' --tests '*ApiContractIntegrationTest'
```

## 롤백 기준

성능 관측 문서만 변경하면 롤백 위험은 낮다. 캐시 구현이 들어가는 경우에는 stale ranking, invalidate 타이밍, 장애 시 DB fallback이 확인되기 전까지 운영 반영하지 않는다.

## 운영 확인

- 랭킹 API p95/p99
- DB CPU
- slow query log
- index scan 사용 여부
- 랭킹 응답 stale 허용 가능 시간

## 진행 기록

| 날짜 | 상태 | 기록 |
| --- | --- | --- |
| 2026-05-06 | 설계 | #40은 런칭 후 지표 기반으로 판단할 성능 관측 묶음으로 분리했다. |

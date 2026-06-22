# Weekly Ranking EXPLAIN ANALYZE Result

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

이 문서는 주간 랭킹 조회 구조 개선 효과를 로컬 테스트 DB에서 다시 측정한 결과입니다.

## 측정 조건

```text
측정일: 2026-04-18
DB: 로컬 PostgreSQL 테스트 DB
DB 이름: postgres
측정 주차: 2026-04-06 ~ 2026-04-13
사용자: 10,000명
이번 주 러닝 기록: 100,000건
사용자별 주간 집계: 10,000건
랭킹 응답 개수: 상위 100명
측정 방식: EXPLAIN (ANALYZE, BUFFERS)
```

측정용 데이터 생성과 쿼리는 `docs/archive/performance/weekly-ranking-benchmark.sql`에 보관했습니다.

주의할 점은 기존 `RankingService` 코드가 이미 `weekly_user_stats` 기반으로 바뀌어 있다는 것입니다.
따라서 기존 방식은 변경 전 Java 코드 자체를 실행한 것이 아니라,
동일 데이터셋에서 `running_records` 100,000건을 주차 기준으로 읽고 사용자별 집계/정렬하는 SQL로 비교했습니다.

또한 이 문서는 HTTP 응답 시간이 아니라 DB 쿼리 실행 시간 기준입니다.
따라서 네트워크, JSON 직렬화, JVM warm-up, DispatcherServlet 초기화, 커넥션 풀 대기 시간은 포함하지 않습니다.
API 성능을 말할 때는 이 결과를 "DB 쿼리 기준 측정"으로 표현해야 합니다.

## 측정 전 확인한 내용

테스트 DB의 `weekly_user_stats` 테이블에는 unique key만 있고,
랭킹 조회용 인덱스는 없었습니다.

```text
있던 인덱스:
weekly_user_stats_pkey
uq_weekly_user_stats_user_week

없던 인덱스:
idx_weekly_user_stats_score_ranking
idx_weekly_user_stats_distance_ranking
```

그래서 측정 SQL에서 아래 인덱스를 먼저 생성했습니다.

```sql
CREATE INDEX IF NOT EXISTS idx_weekly_user_stats_score_ranking
    ON weekly_user_stats (week_start_date, tier_score DESC, total_distance_km DESC, user_id ASC);

CREATE INDEX IF NOT EXISTS idx_weekly_user_stats_distance_ranking
    ON weekly_user_stats (week_start_date, total_distance_km DESC, tier_score DESC, user_id ASC);
```

## 데이터 건수

```text
running_records: 100,000 rows
users: 10,000 rows
weekly_user_stats: 10,000 rows
```

## 점수 랭킹 비교

### 기존 방식에 해당하는 원본 기록 기반 집계

```text
Execution Time: 79.882 ms
Buffers: shared hit=1457
처리 row: running_records 100,000 rows, users 10,000 rows
주요 실행 계획: Seq Scan on running_records -> Hash Join -> HashAggregate -> top-N Sort
```

원본 기록 기반 쿼리는 이번 주 `running_records` 100,000건을 읽고,
사용자별로 10,000개 그룹을 만든 뒤 점수와 거리 기준으로 정렬했습니다.

### 개선 후 weekly_user_stats 기반 조회

```text
Execution Time: 0.170 ms
Buffers: shared hit=402
반환 row: 100 rows
사용 인덱스: idx_weekly_user_stats_score_ranking
주요 실행 계획: Index Scan on weekly_user_stats -> users_pkey Index Scan
```

비교하면 아래와 같습니다.

```text
79.882 ms -> 0.170 ms
약 470배 개선
```

## 거리 랭킹 비교

### 기존 방식에 해당하는 원본 기록 기반 집계

```text
Execution Time: 59.467 ms
Buffers: shared hit=1457
처리 row: running_records 100,000 rows, users 10,000 rows
주요 실행 계획: Seq Scan on running_records -> Hash Join -> HashAggregate -> top-N Sort
```

### 개선 후 weekly_user_stats 기반 조회

```text
Execution Time: 0.163 ms
Buffers: shared hit=402
반환 row: 100 rows
사용 인덱스: idx_weekly_user_stats_distance_ranking
주요 실행 계획: Index Scan on weekly_user_stats -> users_pkey Index Scan
```

비교하면 아래와 같습니다.

```text
59.467 ms -> 0.163 ms
약 365배 개선
```

## 내 랭킹 조회 쿼리

`GET /ranking/me`는 상위 100명 랭킹 조회와 다르게,
내 집계 row를 조회한 뒤 내 앞에 있는 사용자가 몇 명인지 count해서 순위를 계산합니다.

현재 서비스 흐름은 아래 세 쿼리로 나뉩니다.

```text
1. 내 weekly_user_stats row 조회
2. 점수 랭킹에서 나보다 앞선 사용자 수 count
3. 거리 랭킹에서 나보다 앞선 사용자 수 count
```

측정 대상 사용자는 `user_id = 5000`으로 고정했습니다.

```text
week_start_date: 2026-04-06
run_count: 10
total_distance_km: 74.000
tier_score: 1.10
```

### 내 집계 row 조회

```text
Execution Time: 0.008 ms
Buffers: shared hit=3
사용 인덱스: uq_weekly_user_stats_user_week
```

`(user_id, week_start_date)` unique key로 1건만 찾기 때문에 비용이 작습니다.

### 점수 순위 count

```text
Execution Time: 2.452 ms
Buffers: shared hit=262
count 대상 row: 7,893 rows
주요 실행 계획: Seq Scan on weekly_user_stats -> Hash Join -> Aggregate
```

점수 순위는 아래 조건으로 "나보다 앞선 사용자 수"를 셉니다.

```text
tier_score가 더 높거나
tier_score가 같고 total_distance_km가 더 높거나
둘 다 같고 user_id가 더 작은 사용자
```

이번 측정에서 `user_id = 5000`의 점수 순위는 `7,894위`입니다.

### 거리 순위 count

```text
Execution Time: 2.438 ms
Buffers: shared hit=262
count 대상 row: 8,904 rows
주요 실행 계획: Seq Scan on weekly_user_stats -> Hash Join -> Aggregate
```

거리 순위는 아래 조건으로 "나보다 앞선 사용자 수"를 셉니다.

```text
total_distance_km가 더 높거나
total_distance_km가 같고 tier_score가 더 높거나
둘 다 같고 user_id가 더 작은 사용자
```

이번 측정에서 `user_id = 5000`의 거리 순위는 `8,905위`입니다.

### 내 랭킹 count 쿼리에서 확인한 점

상위 100명 점수/거리 랭킹 쿼리는 랭킹용 인덱스를 사용했습니다.
반면 내 랭킹 count 쿼리는 이번 측정에서 `weekly_user_stats`를 순차 스캔했습니다.

이유는 내 랭킹이 "상위 N개를 정렬 순서대로 가져오는 쿼리"가 아니라,
내 기준값보다 앞선 모든 row를 세는 쿼리이기 때문입니다.
현재 데이터셋은 주차별 집계 row가 10,000건이라 약 2.4ms 수준이었지만,
주차별 사용자 수가 훨씬 커지면 이 count 쿼리도 별도 최적화 대상이 될 수 있습니다.

따라서 현재 문서에서 성능 개선 근거로 강하게 말할 수 있는 부분은
`GET /ranking/weekly/score`, `GET /ranking/weekly/distance`의 상위 100명 조회입니다.
`GET /ranking/me`는 원본 기록 전체 재계산은 피했지만,
순위 산정을 위해 주차별 집계 row를 count하는 비용이 남아 있습니다.

## 애플리케이션으로 넘어가는 row 수 관점

기존 Java 방식은 랭킹을 만들기 위해 이번 주 원본 기록과 사용자 목록을 애플리케이션으로 가져와야 했습니다.

```text
running_records: 100,000 rows
users: 10,000 rows
총 전송 대상: 110,000 rows
```

개선 후 현재 구조는 랭킹 상위 100명 기준으로 집계 row와 해당 사용자 정보만 필요합니다.

```text
weekly_user_stats: 100 rows
users: 100 rows
총 전송 대상: 200 rows
```

비교하면 아래와 같습니다.

```text
110,000 rows -> 200 rows
약 550배 감소
```

## 정리

이번 재측정 결과에서도 개선 방향은 명확했습니다.
기존 방식은 주간 원본 기록 수에 비례해 읽기, 그룹핑, 정렬 비용이 발생합니다.
반면 개선 방식은 사용자별 주간 집계 row를 미리 만들어두고,
랭킹 조회 시에는 정렬 기준에 맞춘 인덱스로 상위 100건만 읽습니다.

다만 이 측정은 로컬 테스트 DB의 warm cache 상태에서 수행한 DB 쿼리 기준입니다.
실제 API 응답 시간은 JVM warm-up, 네트워크, JSON 직렬화, 커넥션 풀 상태에 따라 달라질 수 있습니다.
따라서 "API 응답 시간이 몇 배 개선됐다"가 아니라,
"랭킹 조회에 필요한 DB 쿼리 실행 시간과 애플리케이션 전송 row 수가 줄었다"라고 표현하는 것이 정확합니다.

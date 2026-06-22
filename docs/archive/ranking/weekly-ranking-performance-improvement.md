# 주간 랭킹과 티어 조회가 원본 기록 전체 조회에 의존하던 문제

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

관련 이슈: #15

## GitHub 이슈 정리

이슈 제목은 아래처럼 정리했습니다.

```text
주간 집계 테이블 기반 랭킹 조회 구조 개선
```

이슈 라벨은 `enhancement`가 적절합니다.
버그 수정이라기보다 기존 랭킹 기능의 조회 구조, 성능, 데이터 정합성을 개선하는 작업이기 때문입니다.

이 이슈의 핵심 문제는 랭킹 조회 시점마다 원본 러닝 기록을 다시 계산한다는 점입니다.
현재 서비스에서 랭킹으로 제공하는 기능은 아래 세 가지입니다.

- `GET /ranking/weekly/score`: 주간 점수 랭킹
- `GET /ranking/weekly/distance`: 주간 거리 랭킹
- `GET /ranking/me`: 내 주간 점수 순위와 거리 순위

세 API 모두 필요한 값은 결국 사용자별 이번 주 합산 결과입니다.

```text
사용자별 이번 주 러닝 횟수
사용자별 이번 주 총 거리
사용자별 이번 주 평균 페이스
사용자별 이번 주 티어 점수
```

따라서 랭킹을 조회할 때마다 `running_records` 원본을 전부 다시 읽고 계산하기보다,
기록 저장 시점에 사용자별 주간 합산 결과를 `weekly_user_stats`에 미리 누적해두는 구조가 필요합니다.

이 구조의 목적은 단순히 테이블을 하나 더 추가하는 것이 아닙니다.
원본 기록 테이블과 조회용 집계 테이블의 역할을 분리하는 것입니다.

```text
running_records:
러닝 기록 원본 보관

weekly_user_stats:
랭킹, 요약, 현재 티어가 참조하는 사용자별 주간 성적표
```

기록은 저장 횟수보다 조회 횟수가 많을 수 있습니다.
따라서 계산 비용을 매 조회마다 반복하기보다,
저장 시점에 한 번 집계하고 조회 시점에는 이미 계산된 결과를 정렬하는 편이 랭킹 API에 더 적합합니다.

## 왜 이 설계를 검토하게 되었는가

달려에서 러닝 기록은 저장된 뒤 여러 기능의 기준 데이터로 다시 사용됩니다.

- 주간 요약
- 현재 티어
- 점수 랭킹
- 거리 랭킹
- 내 랭킹
- 추후 성장 분석

현재 구조는 기록이 많지 않은 초기 단계에서는 동작에 문제가 없지만,
데이터가 쌓이면 랭킹과 주간 요약 조회 비용이 계속 커지는 구조입니다.

특히 주간 랭킹은 조회될 때마다 이번 주 전체 러닝 기록을 가져온 뒤
Java 메모리에서 사용자별 그룹핑, 합산, 점수 계산, 정렬을 수행합니다.

즉 조회 요청이 들어올 때마다 같은 계산을 반복하고 있습니다.

## 현재 코드의 계산 방식

현재 티어 점수 계산은 여러 서비스에 중복되어 있습니다.

- `RecordService`
- `RankingService`
- `CurrentTierResolver`
- `OnboardingService`

기록 1건의 점수는 아래 구조입니다.

```text
기록 1건 점수 = 페이스 기반 점수 * 거리 가중치
```

코드 기준으로는 아래와 같습니다.

```java
private double calculateTierScore(double distanceKm, int paceSecPerKm) {
    double paceMinutes = round2(paceSecPerKm / 60.0);
    double baseScore = round2(6.00 / paceMinutes);
    double distanceWeight = getDistanceWeight(distanceKm);
    return round2(baseScore * distanceWeight);
}
```

주간 티어 점수는 각 기록 점수의 평균으로 계산합니다.

```java
private double calculateWeeklyTierScore(List<RunningRecord> records) {
    if (records.isEmpty()) {
        return 0.0;
    }
    double totalScore = records.stream()
            .mapToDouble(record -> calculateTierScore(record.getDistanceKm(), record.getAvgPaceSecPerKm()))
            .sum();
    return round2(totalScore / records.size());
}
```

이 방식은 의도적으로 맞습니다.
개별 기록 점수 안에 이미 거리 가중치가 들어가기 때문입니다.

따라서 주간 점수를 다시 거리 가중 평균으로 만들면
장거리 기록이 점수에 두 번 강하게 반영될 수 있습니다.

정리하면 현재 의도는 아래와 같습니다.

```text
개별 기록:
페이스 점수에 거리 가중치를 적용한다.

주간 티어:
기록별 점수의 산술 평균을 사용한다.

이유:
거리 영향은 개별 기록 점수에서 이미 반영되므로,
주간 평균 계산에서는 다시 거리 가중을 적용하지 않는다.
```

## 현재 구조의 성능 문제

현재 랭킹 조회는 대략 아래 흐름입니다.

```text
1. 이번 주 전체 running_records 조회
2. 전체 유저 조회
3. Java에서 userId 기준으로 기록 그룹핑
4. 사용자별 주간 거리, 평균 페이스, 티어 점수 계산
5. Java에서 정렬
6. 순위 부여
```

이 구조의 비용은 데이터가 많아질수록 커집니다.

```text
R = 이번 주 러닝 기록 수
U = 활성 사용자 수

DB 조회량:
running_records R건
users U건

애플리케이션 메모리:
O(R + U)

계산 비용:
그룹핑 O(R)
사용자별 계산 O(R + U)
정렬 O(U log U)
```

문제는 랭킹 API가 조회성 API라는 점입니다.
조회가 자주 일어나는데 매번 원본 기록 전체를 다시 읽고 다시 계산합니다.

예를 들어 이번 주 기록이 10만 건이면,
점수 랭킹을 한 번 조회할 때마다 10만 건을 애플리케이션으로 가져와야 합니다.

거리 랭킹도 비슷한 계산을 반복합니다.
내 랭킹도 전체 기록과 전체 사용자를 기준으로 순위를 다시 계산합니다.

즉 현재 구조는 아래 특징을 가집니다.

- 저장은 가볍습니다.
- 조회는 데이터가 늘수록 무거워집니다.
- 같은 주차의 같은 랭킹 계산을 요청마다 반복합니다.
- DB 인덱스가 정렬 결과 생성에 충분히 활용되기 어렵습니다.
- 애플리케이션 메모리 사용량이 주간 기록 수에 비례합니다.

초기 사용자 수가 적을 때는 문제가 잘 보이지 않지만,
러닝 기록은 누적 데이터이기 때문에 시간이 지날수록 병목이 랭킹 조회에 몰릴 가능성이 큽니다.

## 개선 방향

개선 방향은 원본 기록과 조회용 집계 데이터를 분리하는 것입니다.

```text
running_records
원본 러닝 기록 저장

weekly_user_stats
사용자별 주간 집계 저장
```

핵심은 기록 저장 시점에 주간 집계를 함께 갱신하고,
랭킹과 주간 요약 조회는 `weekly_user_stats`를 기준으로 처리하는 것입니다.

```text
기록 저장 시점:
running_records 저장
weekly_user_stats 갱신

랭킹 조회 시점:
weekly_user_stats 조회
DB에서 정렬
필요한 개수만 응답
```

이렇게 하면 조회 요청 때마다 원본 기록 전체를 다시 계산하지 않아도 됩니다.

## weekly_user_stats 테이블 설계

집계 테이블은 사용자와 주차를 기준으로 한 줄만 유지합니다.

```sql
CREATE TABLE weekly_user_stats (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    week_start_date DATE NOT NULL,

    run_count INTEGER NOT NULL DEFAULT 0,
    total_distance_km NUMERIC(10, 3) NOT NULL DEFAULT 0,
    total_duration_sec INTEGER NOT NULL DEFAULT 0,

    weighted_pace_sum NUMERIC(14, 3) NOT NULL DEFAULT 0,
    avg_pace_sec_per_km INTEGER NOT NULL DEFAULT 0,

    tier_score_sum NUMERIC(10, 2) NOT NULL DEFAULT 0,
    tier_score NUMERIC(5, 2) NOT NULL DEFAULT 0,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_weekly_user_stats_user_week UNIQUE (user_id, week_start_date)
);
```

각 컬럼의 의미는 아래와 같습니다.

- `run_count`: 해당 주차의 러닝 기록 수
- `total_distance_km`: 해당 주차의 총 거리
- `total_duration_sec`: 해당 주차의 총 운동 시간
- `weighted_pace_sum`: 평균 페이스 계산을 위한 `pace * distance` 합
- `avg_pace_sec_per_km`: 거리 가중 평균 페이스
- `tier_score_sum`: 기록별 티어 점수 합
- `tier_score`: `tier_score_sum / run_count`

중요한 점은 평균 페이스와 주간 티어 점수의 평균 방식이 다르다는 것입니다.

평균 페이스는 거리 가중 평균을 씁니다.

```text
avg_pace_sec_per_km = weighted_pace_sum / total_distance_km
```

주간 티어 점수는 기록별 점수의 산술 평균을 씁니다.

```text
tier_score = tier_score_sum / run_count
```

이렇게 나누는 이유는 티어 점수에는 이미 개별 기록 단위에서 거리 가중치가 반영되어 있기 때문입니다.

## 인덱스 설계

랭킹 API는 주차를 기준으로 점수 또는 거리 내림차순 정렬을 합니다.
따라서 인덱스도 조회 패턴에 맞춰 둡니다.

```sql
CREATE INDEX idx_weekly_user_stats_score_ranking
ON weekly_user_stats (week_start_date, tier_score DESC, total_distance_km DESC, user_id ASC);

CREATE INDEX idx_weekly_user_stats_distance_ranking
ON weekly_user_stats (week_start_date, total_distance_km DESC, tier_score DESC, user_id ASC);
```

점수 랭킹은 아래 기준으로 정렬합니다.

```text
tier_score DESC
total_distance_km DESC
user_id ASC
```

거리 랭킹은 아래 기준으로 정렬합니다.

```text
total_distance_km DESC
tier_score DESC
user_id ASC
```

마지막에 `user_id ASC`를 넣는 이유는 동점자가 있을 때 정렬 결과가 매 요청마다 흔들리지 않게 하기 위해서입니다.

## 기록 저장 트랜잭션 설계 재검토

기록 저장은 아래 순서로 처리합니다.

```text
1. 요청 DTO 기본 검증
2. RunningRecordValidator로 도메인 정합성 검증
3. running_records 저장
4. record_outbox_events에 주간 집계 갱신 이벤트 저장
5. 기록 저장 트랜잭션 커밋
6. Scheduler가 outbox 이벤트를 조회
7. 별도 트랜잭션에서 weekly_user_stats 갱신
8. 성공 시 outbox 이벤트 DONE 처리
9. 실패 시 retry_count와 next_retry_at을 갱신해 자동 재시도
```

초기 구현에서는 아래 기준으로 판단했습니다.

```text
러닝 기록 저장 직후 주간 요약과 랭킹이 바로 맞아야 한다.
따라서 running_records 저장과 weekly_user_stats 갱신을 하나의 원자적 작업으로 묶는다.
```

이 기준에서는 `weekly_user_stats` 갱신 메서드에 `Propagation.MANDATORY`를 적용하는 것이 자연스러웠습니다.
`MANDATORY`는 이미 열린 트랜잭션이 있을 때만 참여하고,
외부 트랜잭션 없이 호출되면 예외를 발생시킵니다.

반대로 `REQUIRES_NEW`는 기존 트랜잭션이 있어도 중단하고 별도 트랜잭션을 새로 시작합니다.
기록 저장 흐름 안에서 단순히 `REQUIRES_NEW`를 쓰면 아래 상태가 생길 수 있습니다.

```text
running_records 저장 트랜잭션 시작
weekly_user_stats 갱신은 REQUIRES_NEW로 별도 커밋
이후 running_records 저장 트랜잭션 롤백

=> 원본 기록은 없는데 집계에는 반영된 상태
```

그래서 초기 설계에서는 `REQUIRES_NEW`보다 `MANDATORY`를 선택했습니다.
다만 당시에는 `REQUIRES_NEW`와 비교해 별도 트랜잭션의 실패/롤백 시나리오까지 충분히 문서화하지 않았고,
구현 후 재검토 과정에서 그 근거를 명확히 정리했습니다.

통합 테스트도 처음에는 같은 전제를 검증했습니다.
`WeeklyUserStatsService`를 `@MockBean`으로 대체하고,
`applyRecord()` 호출 시 예외를 강제로 발생시킨 뒤
`running_records`와 `weekly_user_stats`가 모두 저장되지 않는지 확인했습니다.
즉 검증한 것은 실제 DB upsert 실패가 아니라
"집계 갱신 단계에서 런타임 예외가 전파되면 기록 저장 트랜잭션 전체가 롤백되는가"였습니다.

하지만 비즈니스 관점에서 다시 보면 기준을 바꿔야 했습니다.

```text
running_records:
사용자가 실제로 달린 원본 기록

weekly_user_stats:
원본 기록으로 다시 만들 수 있는 조회용 파생 집계
```

주간 집계는 다시 계산할 수 있지만,
사용자의 주행 기록은 쉽게 다시 만들 수 없습니다.
사용자가 기록 저장 버튼을 눌렀는데 집계 갱신 실패 때문에 원본 기록 저장까지 실패하면,
사용자는 같은 주행을 다시 재현할 수 없고 앱 신뢰성에 직접적인 문제가 됩니다.

따라서 개선 후 트랜잭션 경계는 아래처럼 바꿨습니다.

```text
러닝 기록 저장:
원본 기록과 집계 작업 예약을 함께 커밋해야 하는 핵심 트랜잭션

주간 집계 갱신:
원본 기록 커밋 이후 별도 트랜잭션에서 처리하는 파생 데이터 갱신
```

최종 구현은 `running_records`와 `record_outbox_events` 저장을 하나의 트랜잭션으로 묶습니다.
반면 실제 `weekly_user_stats` 갱신은 Scheduler가 outbox 이벤트를 읽어 별도 트랜잭션에서 처리합니다.

```text
RecordService.saveRecord()
-> running_records 저장
-> record_outbox_events 저장
-> 커밋

RecordOutboxEventScheduler
-> PENDING 이벤트 조회
-> PROCESSING 상태로 claim
-> weekly_user_stats upsert
-> outbox 이벤트 DONE 처리
```

이때 `weekly_user_stats` upsert와 outbox `DONE` 처리는 같은 트랜잭션으로 묶었습니다.
그래야 집계는 반영됐는데 이벤트가 아직 `PENDING`으로 남아
다음 재시도 때 중복 집계되는 문제를 막을 수 있습니다.

초기에는 `MANDATORY`를 유지하고 집계 처리용 processor에서 새 트랜잭션을 여는 방식도 검토했습니다.
하지만 기록 저장 커밋 이후 outbox scheduler에서 집계를 처리한다면,
`MANDATORY`를 유지하기 위한 중간 트랜잭션 processor는 과하다고 판단했습니다.
그래서 `MANDATORY`를 제거하고 `applyRecord()`가 자체 트랜잭션으로 집계를 갱신하도록 단순화했습니다.

현재 서비스에는 집계 반영 직후의 실시간 랭킹 정확성을 강하게 요구하는 기능이 없습니다.
따라서 집계 갱신을 원본 기록 저장 트랜잭션과 강하게 묶기보다,
원본 기록을 보존하고 집계가 결국 반영되는 구조를 우선했습니다.

개선 후 실패 테스트는 반대로 바뀌었습니다.
집계 갱신 중 예외를 강제로 발생시켜도 `running_records`는 커밋되고,
outbox 이벤트는 `PENDING` 상태로 돌아가 재시도 대상이 되는지 확인합니다.
즉 검증 기준은 "집계 실패 시 원본 기록을 롤백하는가"가 아니라
"원본 기록은 보존하고 실패한 집계 작업을 자동 재시도할 수 있는가"로 바뀌었습니다.

메시지 큐 도입도 검토했지만, 현재 집계 이벤트는 같은 백엔드 내부에서 소비되고
집계 외 consumer가 아직 없습니다. 또한 메시지 큐를 사용하더라도
DB 커밋 이후 queue publish 전에 서버가 종료되면 이벤트가 유실될 수 있으므로
outbox를 source of truth로 두는 구조가 필요합니다.
따라서 현재 단계에서는 outbox + scheduler 자동 재시도를 먼저 적용하고,
향후 알림, 배지, 추천처럼 consumer가 늘어나면
outbox relay + message queue + consumer 구조로 확장할 수 있게 열어두었습니다.

### Outbox 재시도 정책과 운영 한계

Outbox 이벤트는 아래 상태를 가집니다.

```text
PENDING:
처리 대기 또는 재시도 대기

PROCESSING:
Scheduler가 처리 대상으로 claim한 상태

DONE:
weekly_user_stats 갱신과 outbox 완료 처리가 함께 커밋된 상태

FAILED:
최대 재시도 횟수에 도달해 더 이상 자동 처리하지 않는 상태
```

Scheduler는 `PENDING`이면서 `next_retry_at`이 현재 시각 이전인 이벤트를 조회합니다.
조회 시 `FOR UPDATE SKIP LOCKED`를 사용해 여러 scheduler 인스턴스가 동시에 실행되더라도
같은 이벤트를 중복으로 claim하지 않도록 했습니다.

처리 중 예외가 발생하면 outbox 이벤트를 다시 `PENDING`으로 돌리고,
`retry_count`를 증가시키며 `next_retry_at`을 뒤로 미룹니다.
재시도 간격은 기본 30초에서 시작해 지수 백오프 방식으로 늘어나며,
최대 지연 시간은 기본 30분입니다.

```text
1회 실패: 30초 뒤 재시도
2회 실패: 60초 뒤 재시도
3회 실패: 120초 뒤 재시도
...
최대 지연: 30분
```

무한 재시도는 하지 않습니다.
기본 최대 재시도 횟수는 10회이며,
이 횟수에 도달하면 이벤트를 `FAILED` 상태로 전환하고 `last_error`에 실패 원인을 남깁니다.
`FAILED` 이벤트는 일반 scheduler 처리 대상에서 제외됩니다.

또한 처리 도중 서버가 종료되어 `PROCESSING` 상태로 남는 이벤트를 대비해,
일정 시간 이상 갱신되지 않은 `PROCESSING` 이벤트는 다시 `PENDING`으로 복구합니다.
현재 기본 stale timeout은 300초입니다.

다만 현재 구현은 운영 수준의 Dead Letter 처리까지는 포함하지 않습니다.
즉 `FAILED` 이벤트에 대한 알림, 관리자 재처리 화면, Dead Letter Queue 연동은 아직 없습니다.
현재 단계에서는 `FAILED` 상태와 `last_error`를 남겨 원인 추적이 가능하게 하고,
운영 알림과 수동/자동 재처리 도구는 추후 과제로 분리했습니다.

## 동시성 처리

같은 유저가 같은 주차에 기록을 거의 동시에 저장할 수 있습니다.

단순히 조회 후 수정 방식으로 구현하면 아래 문제가 생길 수 있습니다.

```text
A 요청이 run_count = 0 조회
B 요청도 run_count = 0 조회
A 요청이 run_count = 1 저장
B 요청도 run_count = 1 저장

실제 기대값은 2인데 결과가 1이 됨
```

이런 lost update를 피하려면 집계 갱신을 DB가 원자적으로 처리하게 해야 합니다.

PostgreSQL 기준으로는 `INSERT ... ON CONFLICT DO UPDATE` 방식이 적합합니다.

```sql
INSERT INTO weekly_user_stats (...)
VALUES (...)
ON CONFLICT (user_id, week_start_date)
DO UPDATE SET
    run_count = weekly_user_stats.run_count + EXCLUDED.run_count,
    total_distance_km = weekly_user_stats.total_distance_km + EXCLUDED.total_distance_km,
    total_duration_sec = weekly_user_stats.total_duration_sec + EXCLUDED.total_duration_sec,
    weighted_pace_sum = weekly_user_stats.weighted_pace_sum + EXCLUDED.weighted_pace_sum,
    tier_score_sum = weekly_user_stats.tier_score_sum + EXCLUDED.tier_score_sum,
    avg_pace_sec_per_km = ROUND(
        (weekly_user_stats.weighted_pace_sum + EXCLUDED.weighted_pace_sum)
        / NULLIF(weekly_user_stats.total_distance_km + EXCLUDED.total_distance_km, 0)
    ),
    tier_score = ROUND(
        (weekly_user_stats.tier_score_sum + EXCLUDED.tier_score_sum)
        / NULLIF(weekly_user_stats.run_count + EXCLUDED.run_count, 0),
        2
    ),
    updated_at = CURRENT_TIMESTAMP;
```

이렇게 하면 같은 `(user_id, week_start_date)`에 동시에 저장 요청이 와도
DB가 충돌 row를 기준으로 누적 갱신을 처리합니다.

## 개선 후 랭킹 조회 흐름

점수 랭킹은 더 이상 `running_records` 전체를 조회하지 않습니다.

```sql
SELECT *
FROM weekly_user_stats
WHERE week_start_date = :weekStartDate
  AND run_count > 0
ORDER BY tier_score DESC, total_distance_km DESC, user_id ASC
LIMIT 100;
```

거리 랭킹도 동일하게 집계 테이블을 기준으로 조회합니다.

```sql
SELECT *
FROM weekly_user_stats
WHERE week_start_date = :weekStartDate
  AND run_count > 0
ORDER BY total_distance_km DESC, tier_score DESC, user_id ASC
LIMIT 100;
```

닉네임 등 사용자 정보는 조회된 `user_id` 목록으로 한 번에 가져와 붙입니다.

```text
weekly_user_stats 100건 조회
user_id 목록 추출
users WHERE id IN (...) 조회
Map<Long, User>로 매핑
```

이렇게 하면 랭킹 조회에서 전체 사용자와 전체 기록을 매번 애플리케이션으로 가져오지 않아도 됩니다.

## 현재 방식과 개선 방식의 성능 차이

현재 방식은 조회 시점에 비용을 크게 지불합니다.

```text
점수 랭킹 현재 방식

DB:
이번 주 running_records R건 조회
전체 users U건 조회

Application:
R건 그룹핑
사용자별 합산
사용자별 티어 점수 계산
U명 정렬

응답:
전체 계산 후 필요한 결과 반환
```

개선 방식은 저장 시점에 작은 비용을 추가하고,
조회 시점 비용을 줄입니다.

```text
점수 랭킹 개선 방식

DB:
weekly_user_stats에서 해당 주차 row를 인덱스 순서로 조회
필요한 개수만 LIMIT

Application:
이미 계산된 집계값을 응답 DTO로 변환
필요한 user_id만 조회

응답:
상위 N개만 반환
```

복잡도를 비교하면 아래와 같습니다.

```text
현재 방식:
조회 1회마다 O(R + U + U log U)
메모리 O(R + U)
DB -> Application 전송량 O(R + U)

개선 방식:
기록 저장 1회마다 O(1)에 가까운 집계 upsert 추가
랭킹 조회는 인덱스 기반 O(log S + N)에 가까운 조회
메모리 O(N)
DB -> Application 전송량 O(N)
```

여기서 값의 의미는 아래와 같습니다.

```text
R = 해당 주차 전체 러닝 기록 수
U = 활성 사용자 수
S = 해당 주차 weekly_user_stats row 수
N = 랭킹 응답 개수
```

실제 DB 내부 비용은 데이터 분포와 실행 계획에 따라 달라지지만,
방향성은 명확합니다.

현재 구조는 주간 기록 수 `R`이 늘어날수록 랭킹 조회가 직접 느려집니다.
개선 구조는 주간 기록 수가 늘어나도 랭킹 조회는 사용자별 집계 row와 응답 개수 중심으로 동작합니다.

특히 한 사용자가 한 주에 여러 번 뛰는 서비스 특성상
`running_records` 수는 `weekly_user_stats` row 수보다 빠르게 증가할 수 있습니다.

```text
예시:
활성 사용자 10,000명
사용자당 주간 평균 기록 5건

running_records:
50,000건

weekly_user_stats:
10,000건
```

현재 방식은 랭킹 조회마다 50,000개 원본 기록을 읽고 계산해야 합니다.
개선 방식은 10,000개 집계 row 중 인덱스 정렬 기준으로 필요한 row만 가져오면 됩니다.

## 쓰기 비용이 늘어나는 트레이드오프

개선 방식이 무조건 공짜는 아닙니다.
기록 저장 시점에는 아래 비용이 추가됩니다.

- `weekly_user_stats` upsert 1회
- 같은 유저, 같은 주차 row에 대한 잠금 경합 가능성
- 커밋 이후 집계 이벤트를 처리하는 비용

하지만 달려의 사용 패턴을 보면 이 트레이드오프는 합리적입니다.

러닝 기록 저장은 사용자가 실제 운동을 끝냈을 때 발생하는 쓰기 작업입니다.
반면 랭킹과 주간 요약은 앱 진입, 홈 화면, 랭킹 탭 등에서 반복 조회될 수 있습니다.

즉 읽기 요청이 쓰기 요청보다 많아질 가능성이 큽니다.
따라서 저장 시점에 작은 계산 비용을 지불하고,
조회 시점의 반복 계산을 줄이는 구조가 더 적합합니다.

## 설계상 주의할 점

이 구조를 도입할 때 가장 조심해야 하는 부분은 원본 기록과 집계 데이터의 불일치입니다.

그래서 아래 규칙을 지켜야 합니다.

- 원본 기록 저장은 집계 갱신 실패와 분리해 먼저 커밋합니다.
- 집계 작업 예약은 원본 기록과 같은 트랜잭션에 저장합니다.
- 집계 갱신은 outbox scheduler가 별도 트랜잭션에서 처리합니다.
- 집계 갱신 실패는 outbox 이벤트의 retry_count와 next_retry_at을 갱신해 자동 재시도합니다.
- 기록 삭제나 수정 기능이 생기면 집계에서 delta를 빼거나 재집계하는 정책을 함께 설계합니다.
- 티어 점수 산식은 `TierScoreCalculator` 같은 공통 컴포넌트로 분리합니다.
- 랭킹 정렬 기준은 DB 쿼리와 테스트에서 동일하게 고정합니다.
- 성능 개선 효과는 구현 후 `EXPLAIN ANALYZE`와 더미 데이터로 측정합니다.

특히 산식 중복은 먼저 정리해야 합니다.
현재처럼 여러 서비스가 같은 계산식을 각자 가지고 있으면,
거리 가중치나 점수 기준이 바뀔 때 기능별 결과가 달라질 수 있습니다.

따라서 집계 테이블 구현 전 또는 구현과 동시에 아래 구조로 분리하는 것이 좋습니다.

```text
TierScoreCalculator

calculateRecordScore(distanceKm, paceSecPerKm)
calculateWeeklyScore(tierScoreSum, runCount)
```

이렇게 하면 기록 저장, 랭킹, 현재 티어, 온보딩 예상 티어가 같은 산식을 공유하게 됩니다.

## 성능 측정 계획

이 문서의 성능 차이는 현재 코드 구조와 예상 조회 패턴을 기반으로 한 설계 판단입니다.
실제 포트폴리오에서 "성능 개선"이라고 말하려면 측정 결과가 필요합니다.

구현 후에는 아래 기준으로 측정합니다.

```text
데이터 세트:
사용자 1,000명 / 10,000명
주간 기록 10,000건 / 100,000건 / 1,000,000건

비교 대상:
기존 RankingService 방식
weekly_user_stats 기반 방식

측정 항목:
점수 랭킹 응답 시간
거리 랭킹 응답 시간
내 랭킹 응답 시간
DB query execution time
애플리케이션으로 전송되는 row 수
```

DB에서는 `EXPLAIN ANALYZE`로 아래를 확인합니다.

- 기존 방식이 `running_records` 주간 범위를 얼마나 읽는지
- 개선 방식이 `weekly_user_stats` 인덱스를 사용하는지
- sort 비용이 줄었는지
- 실제 반환 row 수와 스캔 row 수가 줄었는지

## 구현 후 측정 결과

아래 수치는 로컬 TestDB에서 더미 데이터를 넣고 측정한 결과입니다.

측정 데이터는 아래와 같이 구성했습니다.

```text
측정일:
2026-04-12

DB:
로컬 PostgreSQL TestDB

측정 주차:
2026-04-06 ~ 2026-04-13

사용자:
10,000명

이번 주 러닝 기록:
100,000건

사용자별 주간 집계:
10,000건

랭킹 응답 개수:
상위 100명
```

측정은 `EXPLAIN ANALYZE` 기준이며,
동일 데이터셋에서 원본 기록 기반 계산과 집계 테이블 기반 조회를 비교했습니다.

### 점수 랭킹 DB 쿼리 비교

기존 원본 기록 기반 계산은 이번 주 `running_records` 100,000건을 읽고,
사용자별로 그룹핑한 뒤 점수 계산과 정렬을 수행해야 했습니다.

```text
기존 원본 기록 기반 점수 랭킹:
Execution Time: 94.295 ms
처리 흐름: running_records 100,000건 조회 -> 10,000명 그룹핑 -> 점수 계산 -> 정렬 -> 상위 100명
Buffers: shared hit=9509
```

개선 후에는 `weekly_user_stats`에서 이미 계산된 주간 집계를 인덱스 순서로 읽습니다.

```text
개선 후 weekly_user_stats 기반 점수 랭킹:
Execution Time: 0.898 ms
처리 흐름: idx_weekly_user_stats_score_ranking 인덱스 사용 -> 상위 100명 조회
Buffers: shared hit=402
```

비교하면 아래와 같습니다.

```text
94.295 ms -> 0.898 ms
약 105배 개선
```

### 거리 랭킹 DB 쿼리 비교

거리 랭킹도 기존 방식에서는 원본 기록 100,000건을 기준으로
사용자별 거리 합계와 티어 점수를 다시 계산해야 했습니다.

```text
기존 원본 기록 기반 거리 랭킹:
Execution Time: 98.419 ms
처리 흐름: running_records 100,000건 조회 -> 10,000명 그룹핑 -> 거리 합산 -> 정렬 -> 상위 100명
Buffers: shared hit=9509
```

개선 후에는 거리 랭킹용 인덱스를 사용합니다.

```text
개선 후 weekly_user_stats 기반 거리 랭킹:
Execution Time: 1.087 ms
처리 흐름: idx_weekly_user_stats_distance_ranking 인덱스 사용 -> 상위 100명 조회
Buffers: shared hit=402
```

비교하면 아래와 같습니다.

```text
98.419 ms -> 1.087 ms
약 90배 개선
```

### 애플리케이션으로 넘어가는 row 수 비교

기존 Java 서비스 방식은 랭킹을 만들기 위해 아래 데이터를 애플리케이션으로 가져왔습니다.

```text
running_records:
100,000 rows

users:
10,001 rows

총 전송 row:
110,001 rows
```

개선 후에는 랭킹 상위 100명 기준으로 아래 정도만 가져오면 됩니다.

```text
weekly_user_stats:
100 rows

users:
100 rows

총 전송 row:
200 rows
```

즉 애플리케이션으로 넘어가는 row 수는 아래처럼 줄었습니다.

```text
110,001 rows -> 200 rows
약 550배 감소
```

이 차이는 단순히 DB 쿼리 시간이 줄어드는 것보다 더 중요합니다.
기존 구조는 DB에서 많은 데이터를 읽은 뒤 애플리케이션 메모리에서 다시 그룹핑하고 정렬해야 했지만,
개선 구조는 DB 인덱스가 정렬된 결과를 바로 반환하기 때문입니다.

### 개선 후 실제 HTTP 응답 시간

개선 후 로컬 서버를 TestDB에 연결해 실제 랭킹 API도 호출했습니다.
로컬에는 `curl`이 없어 Python `urllib`로 5회 측정했습니다.

```text
GET /ranking/weekly/score
1회차: 220.051 ms
2회차: 62.306 ms
3회차: 45.346 ms
4회차: 37.179 ms
5회차: 38.671 ms

warm-up 이후 대표값:
약 42 ms
```

```text
GET /ranking/weekly/distance
1회차: 20.593 ms
2회차: 19.195 ms
3회차: 19.199 ms
4회차: 17.379 ms
5회차: 17.708 ms

warm-up 이후 대표값:
약 18~19 ms
```

첫 요청은 DispatcherServlet 초기화와 JVM warm-up 영향이 섞여 있어,
대표값에서는 제외하는 것이 맞습니다.

단, 이 HTTP 응답 시간은 개선 후 코드만 측정한 값입니다.
기존 코드의 HTTP 응답 시간은 현재 브랜치에 남아 있지 않기 때문에 직접 비교하지 않았습니다.
대신 기존 방식은 같은 데이터셋에서 원본 기록 기반 SQL 실행 계획으로 비교했습니다.

### 내 랭킹 조회

내 랭킹은 상위 100명을 가져오는 API와 다릅니다.
특정 사용자의

 순위를 계산하려면 "나보다 앞선 사람이 몇 명인지"를 세야 합니다.

이번 측정에서는 중간 순위권 사용자 1명을 기준으로 count 쿼리를 확인했습니다.

```text
scoreRank count query:
Execution Time: 10.844 ms

distanceRank count query:
Execution Time: 4.137 ms
```

기존 방식에서는 내 랭킹을 구할 때도 주간 원본 기록 100,000건과 사용자 목록을 가져와
애플리케이션에서 전체 순위를 다시 계산해야 했습니다.

개선 후에는 원본 기록을 가져오지 않고,
`weekly_user_stats`의 집계 row 기준으로 count 쿼리만 수행합니다.

다만 내 랭킹 count 쿼리는 상위 100명 랭킹 조회만큼 극적으로 줄지는 않습니다.
정확한 순위를 계산하려면 앞선 사용자 수를 세야 하기 때문입니다.
나중에 더 줄이고 싶다면 주간 마감 시점에 rank를 스냅샷으로 저장하는 방식이 필요합니다.

### 설계와 구현 대조 결과

문서에서 의도한 핵심 설계는 코드에 반영되었습니다.

- `weekly_user_stats` 엔티티와 repository를 추가했습니다.
- 기록 저장 시 `running_records`와 `record_outbox_events`를 같은 트랜잭션에 저장합니다.
- scheduler가 outbox 이벤트를 읽어 `weekly_user_stats`를 별도 트랜잭션에서 갱신합니다.
- 집계 갱신은 PostgreSQL `ON CONFLICT DO UPDATE`로 처리합니다.
- `record.startAt` 기준으로 주차를 계산합니다.
- `TierScoreCalculator`로 기록 점수 산식을 공통화했습니다.
- 주간 티어 점수는 기록별 점수의 산술 평균으로 유지했습니다.
- 평균 페이스는 거리 가중 평균으로 계산합니다.
- 점수 랭킹과 거리 랭킹은 `weekly_user_stats`의 인덱스 정렬을 사용합니다.
- 집계 갱신 실패 시 원본 기록은 보존되고 outbox 이벤트가 재시도 대상에 남는 통합 테스트를 추가했습니다.
- 집계 갱신과 outbox `DONE` 처리는 같은 트랜잭션에서 수행하도록 묶었습니다.

설계와 조금 다른 부분도 있습니다.

- 랭킹 조회 쿼리에서 활성 사용자와 닉네임 존재 여부를 필터링하기 위해 `users`를 join합니다.
- 이후 응답 DTO 구성을 위해 조회된 `user_id` 목록으로 `users`를 한 번 더 조회합니다.
- 이 구조는 동작상 문제는 없지만, projection으로 `nickname`까지 한 번에 가져오면 user 추가 조회를 줄일 수 있습니다.
- 티어 메타데이터 조회는 현재 응답 row마다 `TierService`를 호출합니다.
- 상위 100명 기준이라 기존 구조보다 훨씬 줄었지만, 추가 최적화를 하려면 티어 메타데이터 캐싱을 고려할 수 있습니다.

이 측정을 기준으로 아래 문장을 근거 있게 사용할 수 있습니다.

```text
랭킹 API가 원본 러닝 기록 전체를 매 요청마다 조회하던 구조를
사용자별 주간 집계 테이블 기반 조회로 변경하고,
주차/점수/거리 기준 인덱스를 추가해 조회 비용을 줄였습니다.
```

더 구체적으로는 아래처럼 쓸 수 있습니다.

```text
TestDB에서 사용자 10,000명, 주간 러닝 기록 100,000건을 기준으로 측정한 결과,
원본 기록 기반 점수 랭킹 쿼리는 94.295ms가 걸렸고,
주간 집계 테이블 기반 쿼리는 0.898ms로 줄었습니다.
거리 랭킹도 98.419ms에서 1.087ms로 줄어,
랭킹 조회가 원본 기록 수에 직접 비례하던 병목을 완화했습니다.
```

## 이 설계로 얻는 효과

이 설계를 적용하면 백엔드 관점에서 아래 역량을 실제 코드로 보여줄 수 있습니다.

- 트랜잭션 경계 설정
- 원본 데이터와 조회 모델 분리
- 집계 테이블 설계
- 랭킹 조회 인덱스 설계
- 동시성 갱신 처리
- API 응답 성능 개선 근거 확보
- 티어 점수 산식 중복 제거

결국 핵심은 단순히 테이블을 하나 더 만드는 것이 아닙니다.

```text
원본 기록은 추적 가능하게 보존하고,
반복 조회되는 랭킹/요약 데이터는 조회에 맞는 형태로 따로 유지하는 것
```

이 구조가 있어야 러닝 기록이 늘어나도 랭킹과 주간 요약 API가 원본 기록 수에 직접 끌려가지 않습니다.

## 커밋 분리 기준

이슈 #15의 변경은 한 번에 구현하면 커 보이지만, 논리적으로는 아래 단위로 나눌 수 있습니다.

```text
refactor: 티어 점수 계산 로직 공통화
```

기록 점수 계산 로직이 `RecordService`, `RankingService`, `CurrentTierResolver`, `OnboardingService`에 흩어져 있던 문제를 먼저 정리합니다.
점수 산식이 바뀌어도 기능별 결과가 달라지지 않도록 `TierScoreCalculator`를 단일 기준으로 둡니다.

```text
feat: 러닝 기록 저장 주간 집계 갱신 추가
```

`weekly_user_stats` 엔티티, repository, service를 추가하고,
`running_records` 저장과 outbox 이벤트 저장을 같은 트랜잭션으로 묶습니다.
이후 scheduler가 outbox 이벤트를 읽어 `weekly_user_stats` 갱신을 별도 트랜잭션에서 수행합니다.
PostgreSQL `INSERT ... ON CONFLICT DO UPDATE`를 사용해 같은 사용자와 같은 주차의 동시 저장도 DB에서 원자적으로 누적 처리합니다.

```text
perf: 랭킹 조회 주간 집계 테이블 전환
```

점수 랭킹, 거리 랭킹, 내 랭킹이 원본 `running_records` 전체 계산 대신
`weekly_user_stats`를 기준으로 조회하도록 변경합니다.
주차/점수/거리 정렬 기준에 맞춘 인덱스를 사용해 top 100 랭킹 조회 비용을 줄입니다.

테스트는 각 커밋에 관련된 테스트를 함께 포함하는 것이 좋습니다.
특히 집계 누적 테스트와 집계 실패 시 원본 기록 보존 테스트는
트랜잭션 정책을 보여주는 근거가 되므로 기능 커밋에 포함하는 편이 자연스럽습니다.

## 결과적으로 바뀐 구조

기존 구조는 조회 시점 계산 방식이었습니다.

```text
랭킹 조회 요청
-> 이번 주 running_records 전체 조회
-> 사용자별 그룹핑
-> 총 거리 계산
-> 평균 페이스 계산
-> 기록별 점수 계산
-> 사용자별 주간 점수 계산
-> 정렬
-> 응답 생성
```

변경 후 구조는 저장 시점 집계 방식입니다.

```text
러닝 기록 저장
-> running_records에 원본 기록 저장
-> record_outbox_events에 집계 작업 예약
-> 원본 기록 트랜잭션 커밋
-> scheduler가 outbox 이벤트 조회
-> 별도 트랜잭션에서 weekly_user_stats upsert
-> 성공 시 outbox DONE 처리
-> 랭킹 조회 시 weekly_user_stats 정렬 조회
-> top 100 또는 내 순위 응답 생성
```

즉 핵심 변화는 아래 한 문장으로 정리할 수 있습니다.

```text
랭킹 조회마다 원본 기록을 다시 계산하던 구조를,
기록 저장 시 사용자별 주간 성적표를 미리 갱신하고 조회 시에는 성적표만 정렬하는 구조로 바꿨다.
```

이 변경으로 점수 랭킹과 거리 랭킹은 원본 기록 수에 직접 비례하지 않고,
사용자별 주간 집계 row와 응답 개수 중심으로 동작하게 됩니다.

## 현재 랭킹 구현 방식

현재 랭킹은 별도 랭킹 시스템이나 Redis sorted set을 사용하지 않습니다.
PostgreSQL에 사용자별 주간 집계 테이블을 두고,
그 테이블을 `ORDER BY`로 정렬해서 랭킹을 만드는 방식입니다.

```text
running_records
러닝 원본 기록 저장

weekly_user_stats
사용자별 주간 합산 결과 저장

ranking API
weekly_user_stats를 ORDER BY로 정렬해서 랭킹 응답 생성
```

점수 랭킹은 아래 기준으로 정렬합니다.

```sql
ORDER BY s.tier_score DESC, s.total_distance_km DESC, s.user_id ASC
LIMIT 100
```

거리 랭킹은 아래 기준으로 정렬합니다.

```sql
ORDER BY s.total_distance_km DESC, s.tier_score DESC, s.user_id ASC
LIMIT 100
```

즉 현재 구조는 아래처럼 정리할 수 있습니다.

```text
RDB 집계 테이블 + 복합 인덱스 + ORDER BY + LIMIT
```

이 방식은 현재 서비스 단계에서 적절합니다.

- 주간 랭킹은 특정 주차 기준으로 조회합니다.
- 응답은 상위 100명 중심입니다.
- 집계 row는 원본 기록 수보다 훨씬 적습니다.
- PostgreSQL 복합 인덱스로 정렬 조회를 최적화할 수 있습니다.
- 별도 Redis 랭킹 동기화나 캐시 무효화 정책을 아직 도입하지 않아도 됩니다.

다만 이 방식은 정확한 내 순위를 구할 때는 한계가 있습니다.
내 순위는 `나보다 앞선 사용자 수 + 1`을 계산해야 하므로,
상위 100명 조회보다 count 비용이 더 들 수 있습니다.
사용자 수와 조회량이 더 커지면 주간 마감 스냅샷 또는 랭킹 캐시를 별도로 검토할 수 있습니다.

## PR 리뷰 반영: NULLIF와 COALESCE

`weekly_user_stats` upsert SQL에서는 평균 페이스와 티어 점수를 다시 계산합니다.

```text
avg_pace_sec_per_km = weighted_pace_sum / total_distance_km
tier_score = tier_score_sum / run_count
```

나누기 계산은 분모가 0이면 문제가 생깁니다.
그래서 처음 구현에서는 `NULLIF`로 0 나누기 에러를 피했습니다.

```sql
값 / NULLIF(분모, 0)
```

`NULLIF(분모, 0)`은 분모가 0이면 `NULL`을 반환합니다.
이렇게 하면 0으로 나누는 DB 에러는 피할 수 있지만,
계산 결과 자체가 `NULL`이 될 수 있습니다.

문제는 `avg_pace_sec_per_km`와 `tier_score` 컬럼이 `NOT NULL`이라는 점입니다.

```text
계산 결과 NULL
-> NOT NULL 컬럼에 NULL 저장 시도
-> DB 제약 조건 위반 가능
```

정상 API 흐름에서는 `RunningRecordValidator`가 거리, 시간, 페이스 정합성을 검증하므로
분모가 0이 될 가능성은 낮습니다.
하지만 더미 데이터, 운영 테스트, 마이그레이션, 직접 DB 조작처럼
애플리케이션 검증을 우회하는 경로에서는 비정상 집계 row가 생길 수 있습니다.

그래서 upsert SQL 자체도 `NOT NULL` 컬럼의 invariant를 지키도록
`COALESCE`를 추가했습니다.

```sql
avg_pace_sec_per_km = COALESCE(CAST(ROUND(
    (weekly_user_stats.weighted_pace_sum + EXCLUDED.weighted_pace_sum)
    / NULLIF(weekly_user_stats.total_distance_km + EXCLUDED.total_distance_km, 0)
) AS INTEGER), 0),
tier_score = COALESCE(ROUND(
    (weekly_user_stats.tier_score_sum + EXCLUDED.tier_score_sum)
    / NULLIF(weekly_user_stats.run_count + EXCLUDED.run_count, 0),
    2
), 0)
```

이 변경의 의미는 아래와 같습니다.

```text
정상 데이터:
기존과 동일하게 계산된 평균 페이스와 티어 점수를 저장

분모가 0인 비정상 데이터:
NULL 대신 0을 저장해 NOT NULL 제약을 유지
```

이 리뷰에서 얻은 기준은 명확합니다.

```text
서버 검증이 있더라도 DB 쿼리도 스키마 제약을 스스로 지키게 만든다.
```

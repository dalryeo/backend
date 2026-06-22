# Record Domain

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-22

## 도메인 개요

Record 도메인은 사용자의 러닝 기록을 서비스가 신뢰할 수 있는 기준 데이터로 받아들이는 도메인이다. 하나의 러닝 기록은 주간 요약, 랭킹, 티어 점수, 기록 목록의 공통 원천이므로 저장 시점의 검증이 도메인의 핵심이다.

Record는 원본 기록을 보존하고, 주간 단위로 반복 조회되는 값은 `weekly_user_stats` read model로 관리한다. 저장과 집계 반영은 outbox를 통해 분리되어 있으며, 저장 성공과 집계 반영은 같은 순간을 보장하지 않는다.

## 핵심 책임

- 러닝 기록 저장 요청을 검증한다.
- 유효한 러닝 기록을 원본 기록으로 저장한다.
- 기록 저장 후 주간 집계 갱신 작업을 예약한다.
- 사용자별 현재 주차 기록 목록을 제공한다.
- 사용자별 주간 요약에 필요한 집계 값을 제공한다.

## 도메인 경계

Record 도메인이 책임지는 것:

- 러닝 기록으로 성립하는 입력인지 판단한다.
- `running_records` 원본 데이터를 저장한다.
- 기록의 서비스 시간대 기준 주차를 결정한다.
- `weekly_user_stats`에 주간 기록 수, 거리, 시간, 평균 페이스, 티어 점수 집계를 원본 기준으로 갱신한다.
- 집계 반영 실패 시 재시도 가능한 outbox 이벤트로 남긴다.

Record 도메인이 책임지지 않는 것:

- 랭킹 순위를 정의하지 않는다.
- 티어 구간을 해석하거나 현재 표시 티어를 확정하지 않는다.
- 사용자 인증 자체를 처리하지 않는다.
- 사용자 상태의 원본 정책을 소유하지 않는다.
- 현재 저장 계약에 없는 idempotency key를 임의로 가정하지 않는다.

## 주요 모델

### 러닝 기록

_Entity_

`RunningRecord`는 사용자가 수행한 러닝 세션의 원본 기록이다.

#### 속성

- `id`: 기록 식별자
- `userId`: 기록을 소유한 사용자
- `platform`: 기록 출처. DB 컬럼명은 `source`다.
- `distanceKm`: 러닝 거리
- `durationSec`: 러닝 시간
- `avgPaceSecPerKm`: 평균 페이스
- `avgHeartRate`: 선택 심박수
- `caloriesKcal`: 선택 칼로리
- `startAt`: 서비스 시간대 기준 시작 시각
- `endAt`: 서비스 시간대 기준 종료 시각

#### 규칙

- 기록은 인증된 활성 사용자에게만 저장된다.
- 기록의 시작/종료 시각은 offset이 포함된 요청값을 서비스 시간대로 변환해 저장한다.
- 저장된 원본 기록은 주간 요약과 집계의 기준이 된다.
- 현재 저장 요청에는 클라이언트 idempotency key가 없으므로, 같은 요청이 반복되면 별도 방어가 없는 한 별도 기록으로 저장될 수 있다.

### 러닝 기록 요청

_Input Contract_

`RunningRecordRequest`는 저장 전 검증 대상이다.

#### 속성

- `platform`: `IOS`, `ANDROID`, `APPLE_WATCH`, `GALAXY_WATCH`
- `distanceKm`: `0.1` 이상 `100.0` 이하
- `durationSec`: `60` 이상 `43200` 이하
- `avgPaceSecPerKm`: `120` 이상 `3600` 이하
- `avgHeartRate`: 없거나 `0`, 또는 `30` 이상 `240` 이하
- `caloriesKcal`: 없거나 `0`, 또는 `1` 이상 `10000` 이하
- `startAt`, `endAt`: offset을 포함한 `OffsetDateTime`

#### 규칙

- `0`으로 들어온 선택 센서 값은 저장 직전에 `null`로 정규화한다.
- DTO 필드 검증을 통과해도 도메인 검증을 다시 통과해야 한다.

### 러닝 기록 검증

_Domain Policy_

`RunningRecordValidator`는 러닝 기록이 서비스 전체의 기준 데이터로 사용할 수 있는지 확인한다.

#### 규칙

- `endAt`은 `startAt`보다 뒤여야 한다.
- 요청의 `durationSec`와 실제 `endAt - startAt` 차이는 5초 이하여야 한다.
- 요청의 `avgPaceSecPerKm`와 `durationSec / distanceKm` 계산값 차이는 15초/km 이하여야 한다.
- `startAt` 또는 `endAt`은 서버 기준 현재 시각보다 5분을 초과해 미래일 수 없다.
- 지원하지 않는 `platform`은 저장하지 않는다.
- 유효하지 않은 기록은 저장 후 집계에서 걸러내지 않고, 저장 전에 차단한다.

### 주간 사용자 집계

_Read Model_

`WeeklyUserStats`는 사용자와 주차 기준으로 계산되는 주간 집계 row다.

#### 속성

- `userId`: 집계 대상 사용자
- `weekStartDate`: 주간 기준일
- `runCount`: 주간 기록 수
- `totalDistanceKm`: 주간 총거리
- `totalDurationSec`: 주간 총시간
- `weightedPaceSum`: 거리 가중 평균 페이스 계산용 누적값
- `avgPaceSecPerKm`: 주간 평균 페이스
- `tierScoreSum`: 기록별 티어 점수 합계
- `tierScore`: 주간 티어 점수 평균

#### 규칙

- 하나의 사용자와 하나의 주차에는 하나의 집계 row만 존재한다.
- 주차는 기록의 `startAt`을 서비스 시간대 날짜로 본 뒤, 해당 날짜의 월요일로 계산한다.
- 집계 갱신은 영향을 받은 사용자와 주차의 `running_records`를 다시 집계한 뒤 `weekly_user_stats` 값을 교체한다.
- 같은 outbox 이벤트가 다시 처리되어도 같은 원본 기준 집계를 다시 쓰므로, 동일 기록이 중복 누적되면 안 된다.
- 평균 페이스는 거리 가중 평균으로 계산한다.
- 주간 티어 점수는 기록별 티어 점수의 평균이다.
- 집계 row는 ranking과 weekly tier finalization의 입력으로 사용된다.

### 기록 Outbox 이벤트

_Domain Event_

`RecordOutboxEvent`는 기록 저장 후 주간 집계 반영을 예약하는 이벤트다.

#### 상태

- `PENDING`: 처리 대기
- `PROCESSING`: 처리 중
- `DONE`: 처리 완료
- `FAILED`: 재시도 한도 초과

#### 규칙

- 기록 저장과 outbox 이벤트 생성은 같은 저장 흐름에 속한다.
- 주간 집계 반영은 별도 트랜잭션에서 수행된다.
- 처리 실패 시 재시도 대상이 되며, 원본 기록은 보존된다.
- 재시도나 stale reset으로 같은 이벤트가 다시 처리될 수 있으므로, 집계 반영은 멱등해야 한다.
- outbox 지연이나 실패가 있으면 `running_records`와 `weekly_user_stats`가 일시적으로 어긋날 수 있다.

## 도메인 규칙

- Record는 유효한 러닝 기록의 저장 관문이다.
- 러닝 기록의 시간 필드는 offset이 포함된 요청값이어야 한다.
- 서비스 기준 시간대는 현재 `Asia/Seoul`이다.
- 주간 기준은 월요일 시작 주차다.
- 저장 직후 주간 요약, 랭킹, 티어 관련 값이 즉시 반영된다고 가정하면 안 된다.
- 원본 기록을 삭제하거나 수정하는 기능을 추가할 때는 영향을 받은 사용자와 주차의 `weekly_user_stats` 재집계 정책이 함께 필요하다.
- Record는 현재 표시 티어를 소유하지 않는다. 요약 응답에 티어가 포함되더라도 표시 티어 기준은 Tier/WeeklyTier 쪽 스냅샷 정책을 따른다.

## 타 도메인과의 관계

| 도메인 | 관계 |
| --- | --- |
| User | 기록 저장 대상 사용자를 확인한다. 사용자 상태의 원본 정책은 User/Auth 쪽에 있다. |
| Tier | 기록별 티어 점수 계산 기준을 제공한다. Record는 점수 계산기를 사용하지만 티어 구간 해석을 소유하지 않는다. |
| Ranking | `weekly_user_stats`를 통해 랭킹 원천 데이터를 제공한다. Ranking은 이 집계를 소비한다. |
| WeeklyTier | 완료된 주간 집계를 사용해 주간 티어 스냅샷을 확정한다. Record는 스냅샷을 확정하지 않는다. |

## 구현 앵커

### API

- `POST /records`
- `GET /records/summary`
- `GET /records/weekly`
- `GET /weekly/summary/current`
- `GET /weekly/summary/list`

### 코드

- [RecordService](../../src/main/java/com/ohgiraffers/dalryeo/record/service/RecordService.java)
- [RunningRecordValidator](../../src/main/java/com/ohgiraffers/dalryeo/record/service/RunningRecordValidator.java)
- [WeeklyUserStatsService](../../src/main/java/com/ohgiraffers/dalryeo/record/service/WeeklyUserStatsService.java)
- [RunningRecordRequest](../../src/main/java/com/ohgiraffers/dalryeo/record/dto/RunningRecordRequest.java)
- [RunningRecord](../../src/main/java/com/ohgiraffers/dalryeo/record/entity/RunningRecord.java)
- [WeeklyUserStats](../../src/main/java/com/ohgiraffers/dalryeo/record/entity/WeeklyUserStats.java)
- [WeeklyUserStatsRepository](../../src/main/java/com/ohgiraffers/dalryeo/record/repository/WeeklyUserStatsRepository.java)
- [RecordOutboxEvent](../../src/main/java/com/ohgiraffers/dalryeo/record/outbox/RecordOutboxEvent.java)
- [RecordOutboxEventProcessor](../../src/main/java/com/ohgiraffers/dalryeo/record/outbox/RecordOutboxEventProcessor.java)

### 대표 테스트

- [RunningRecordValidatorTest](../../src/test/java/com/ohgiraffers/dalryeo/record/service/RunningRecordValidatorTest.java)
- [RecordServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/record/service/RecordServiceTest.java)
- [RecordSaveTransactionIntegrationTest](../../src/test/java/com/ohgiraffers/dalryeo/record/service/RecordSaveTransactionIntegrationTest.java)
- [RecordAggregationIntegrationTest](../../src/test/java/com/ohgiraffers/dalryeo/record/service/RecordAggregationIntegrationTest.java)
- [RecordOutboxEventTransactionServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/record/outbox/RecordOutboxEventTransactionServiceTest.java)

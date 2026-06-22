# Ranking Domain

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-16

## 도메인 개요

Ranking 도메인은 달려 사용자의 주간 러닝 성과를 순위로 보여주는 도메인이다. 랭킹은 기록을 직접 소유하지 않고, record 도메인이 만든 주간 집계 결과를 기준으로 순위를 정의한다.

랭킹은 경쟁 화면의 표시값이면서 동시에 사용자에게 이번 주 성과를 설명하는 기준이다. 따라서 목록 랭킹과 내 랭킹은 항상 같은 순위 정의를 사용해야 한다.

## 핵심 책임

- 현재 주차의 점수 랭킹 목록을 제공한다.
- 현재 주차의 거리 랭킹 목록을 제공한다.
- 로그인 사용자의 점수 순위와 거리 순위를 제공한다.
- 랭킹 응답에 표시할 현재 티어 정보를 붙인다.
- 랭킹 대상에서 제외해야 하는 사용자를 걸러낸다.

## 도메인 경계

Ranking 도메인이 책임지는 것:

- `weekly_user_stats`를 기준으로 순위를 계산한다.
- 점수 랭킹과 거리 랭킹의 정렬 기준을 정의한다.
- 내 랭킹을 목록 랭킹과 같은 기준으로 계산한다.
- 랭킹에 노출 가능한 사용자만 응답에 포함한다.

Ranking 도메인이 책임지지 않는 것:

- 원본 러닝 기록을 저장하거나 검증하지 않는다.
- 주간 집계 row를 생성하거나 갱신하지 않는다.
- 티어 점수를 계산하지 않는다.
- 현재 표시 티어를 확정하지 않는다.
- 탈퇴, 닉네임, 사용자 상태의 원본 정책을 소유하지 않는다.

## 주요 모델

### 주간 랭킹 집계

_Read Model_

`weekly_user_stats`는 랭킹의 원천 read model이다. Ranking은 원본 `running_records`를 매 요청마다 다시 계산하지 않는다.

#### 속성

- `userId`: 랭킹 대상 사용자
- `weekStartDate`: 랭킹 기준 주차의 월요일
- `runCount`: 주간 기록 수
- `totalDistanceKm`: 주간 총거리
- `avgPaceSecPerKm`: 주간 평균 페이스
- `tierScore`: 주간 티어 점수

#### 규칙

- 랭킹은 현재 주차의 `weekly_user_stats`만 대상으로 한다.
- `runCount`가 0인 row는 랭킹 대상이 아니다.
- 집계 row가 아직 반영되지 않은 기록은 랭킹에 즉시 보이지 않을 수 있다.

### 점수 랭킹

_Ranking Policy_

점수 랭킹은 주간 티어 점수가 높은 사용자를 먼저 보여주는 순위다.

#### 정렬 기준

1. `tierScore`가 높을수록 앞선다.
2. `tierScore`가 같으면 `totalDistanceKm`가 높을수록 앞선다.
3. `tierScore`와 `totalDistanceKm`가 같으면 `userId`가 작을수록 앞선다.

#### 규칙

- 점수 랭킹 목록과 내 점수 순위는 같은 정렬 정의를 사용해야 한다.
- 동점 처리 기준이 바뀌면 목록 순위와 내 순위가 서로 모순되면 안 된다.

### 거리 랭킹

_Ranking Policy_

거리 랭킹은 주간 총거리가 긴 사용자를 먼저 보여주는 순위다.

#### 정렬 기준

1. `totalDistanceKm`가 높을수록 앞선다.
2. `totalDistanceKm`가 같으면 `tierScore`가 높을수록 앞선다.
3. `totalDistanceKm`와 `tierScore`가 같으면 `userId`가 작을수록 앞선다.

#### 규칙

- 거리 랭킹 목록과 내 거리 순위는 같은 정렬 정의를 사용해야 한다.
- 거리 동점 처리 기준은 점수 랭킹의 동점 처리 기준과 독립적으로 관리한다.

### 내 랭킹

_Query Result_

내 랭킹은 로그인 사용자의 현재 주차 위치를 나타낸다.

#### 속성

- `scoreRank`: 점수 랭킹에서의 내 순위
- `distanceRank`: 거리 랭킹에서의 내 순위
- `tierScore`: 현재 주차 점수
- `weeklyAvgPace`: 현재 주차 평균 페이스
- `weeklyDistance`: 현재 주차 총거리

#### 규칙

- 현재 주차 기록이 없으면 `scoreRank`와 `distanceRank`는 `null`이다.
- 현재 주차 기록이 없으면 점수, 평균 페이스, 총거리는 0 기준으로 응답한다.
- 내 순위는 "나보다 앞선 사용자 수 + 1"로 계산한다.
- 내 순위 계산은 상위 100명 목록에 포함되는지 여부와 독립적이어야 한다.

## 도메인 규칙

- 랭킹 기준 주차는 서비스 시간대의 현재 주 시작일이다. 현재 기본 시간대는 `Asia/Seoul`이고 주 시작일은 월요일이다.
- 랭킹 대상 사용자는 탈퇴 상태가 아니어야 한다.
- 랭킹 대상 사용자는 닉네임이 있어야 한다.
- 랭킹 목록은 현재 상위 100명까지 제공한다.
- 랭킹은 `weekly_user_stats`의 집계 지연을 허용한다. 저장 성공과 랭킹 반영은 같은 순간을 보장하지 않는다.
- 랭킹 응답의 티어 표시는 현재 주 기록 점수로 즉시 계산하지 않는다. 현재 표시 티어는 확정된 `weekly_tiers` 스냅샷을 기준으로 한다.
- 확정된 티어 스냅샷이 없으면 랭킹 응답의 티어 표시는 `TURTLE / B`를 기본값으로 사용한다.
- Ranking 도메인은 Redis ranking set, ranking snapshot, 애플리케이션 캐시를 현재 기준으로 소유하지 않는다.

## 타 도메인과의 관계

| 도메인 | 관계 |
| --- | --- |
| Record | `weekly_user_stats`를 통해 주간 기록 집계 결과를 제공한다. Ranking은 이 집계를 소비한다. |
| Tier | 랭킹 응답에 표시할 현재 티어 정보를 제공한다. Ranking은 티어를 확정하거나 점수 구간을 직접 해석하지 않는다. |
| User | 랭킹 대상 사용자의 닉네임과 상태를 제공한다. 탈퇴 사용자와 닉네임 없는 사용자는 랭킹에서 제외된다. |

## 구현 앵커

### API

- `GET /ranking/weekly/score`
- `GET /ranking/weekly/distance`
- `GET /ranking/me`

### 코드

- [RankingService](../../src/main/java/com/ohgiraffers/dalryeo/ranking/service/RankingService.java)
- [RankingController](../../src/main/java/com/ohgiraffers/dalryeo/ranking/controller/RankingController.java)
- [WeeklyUserStatsRepository](../../src/main/java/com/ohgiraffers/dalryeo/record/repository/WeeklyUserStatsRepository.java)
- [CurrentWeeklyTierResolver](../../src/main/java/com/ohgiraffers/dalryeo/tier/service/CurrentWeeklyTierResolver.java)

### 대표 테스트

- [RankingServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/ranking/service/RankingServiceTest.java)
- [RecordAggregationIntegrationTest](../../src/test/java/com/ohgiraffers/dalryeo/record/service/RecordAggregationIntegrationTest.java)

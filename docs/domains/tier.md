# Tier Domain

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-16

## 도메인 개요

Tier 도메인은 러닝 성과 점수를 티어 표시값으로 해석하는 도메인이다. 달려에서 티어는 단순 계산 결과가 아니라, 사용자가 자신의 현재 위치를 이해하는 표시 정책이다.

Tier 도메인에서 가장 중요한 구분은 "현재 주 기록으로 계산한 점수"와 "현재 표시 티어"가 다르다는 점이다. 현재 표시 티어는 완료된 주간 기록을 확정한 `weekly_tiers` 스냅샷을 기준으로 한다.

## 핵심 책임

- 러닝 기록 1건의 티어 점수를 계산한다.
- 주간 티어 점수를 계산한다.
- 점수를 티어 코드, 등급, 표시 이름, 기본 프로필 이미지로 해석한다.
- 완료된 주간 집계를 주간 티어 스냅샷으로 확정한다.
- 현재 표시 티어를 확정된 스냅샷 기준으로 제공한다.

## 도메인 경계

Tier 도메인이 책임지는 것:

- 기록 점수와 주간 점수 계산 정책을 정의한다.
- `tier_grade` 메타데이터를 읽고 점수 구간을 해석한다.
- `weekly_tiers` 스냅샷을 기준으로 현재 표시 티어를 결정한다.
- 스냅샷이 없는 상태를 명확히 반환해 호출자가 fallback을 결정하게 한다.

Tier 도메인이 책임지지 않는 것:

- 원본 러닝 기록을 저장하거나 검증하지 않는다.
- 주간 집계 row를 생성하지 않는다.
- 랭킹 순위를 정의하지 않는다.
- 온보딩 정보를 저장하지 않는다.
- 사용자 프로필 이미지 업로드 정책을 소유하지 않는다.

## 주요 모델

### 기록 점수

_Domain Policy_

기록 점수는 러닝 기록 1건의 거리와 평균 페이스로 계산한다.

#### 속성

- `distanceKm`: 러닝 거리
- `paceSecPerKm`: 평균 페이스
- `distanceWeight`: 거리 구간별 가중치

#### 계산 기준

```text
paceMinutes = round2(paceSecPerKm / 60.0)
baseScore = round2(6.00 / paceMinutes)
recordScore = round2(baseScore * distanceWeight)
```

#### 거리 가중치

| 거리 | 가중치 |
| --- | --- |
| `< 1km` | `0.50` |
| `< 2km` | `0.60` |
| `< 3km` | `0.70` |
| `< 5km` | `1.00` |
| `< 7km` | `1.03` |
| `< 9km` | `1.05` |
| `< 11km` | `1.06` |
| `< 15km` | `1.07` |
| `< 25km` | `1.08` |
| `< 40km` | `1.09` |
| `>= 40km` | `1.10` |

#### 규칙

- 기록 점수는 소수점 둘째 자리로 반올림한다.
- 거리와 페이스 값의 유효성은 Record 도메인의 저장 검증을 우선 통과해야 한다.
- 기록 점수 산식은 record 집계, ranking, onboarding 예상 티어, weekly tier finalization에서 같은 기준으로 사용되어야 한다.

### 주간 티어 점수

_Domain Policy_

주간 티어 점수는 한 주의 기록 점수 평균이다.

#### 계산 기준

```text
weeklyScore = round2(tierScoreSum / runCount)
```

#### 규칙

- 기록 수가 0이면 주간 점수는 0이다.
- 주간 점수는 `weekly_user_stats.tier_score`에 저장된다.
- 주간 점수는 완료된 주간 티어 스냅샷을 확정할 때 사용된다.

### 티어 메타데이터

_Reference Model_

`TierGrade`는 점수 구간을 사용자에게 보여줄 티어 표시값으로 바꾸는 기준이다.

#### 속성

- `tierCode`: 티어 코드
- `displayName`: 표시 이름
- `grade`: 티어 등급
- `minScore`: 구간 최소 점수
- `maxScore`: 구간 최대 점수
- `defaultProfileImage`: 티어 기본 프로필 이미지

#### 규칙

- 티어 메타데이터의 원본은 Flyway migration으로 관리한다.
- 애플리케이션 시작 시 `TierMetadataCache`가 `tier_grade` 전체를 읽어 메모리에 올린다.
- 메타데이터가 비어 있으면 서버는 빠르게 실패한다.
- 운영 중 DB를 직접 수정해도 실행 중인 서버 캐시에는 자동 반영되지 않는다.
- 즉시 반영이 필요하면 캐시 reload 경로나 관리자 기능을 별도로 설계해야 한다.
- 앱 시작 시 코드가 티어 메타데이터를 새로 쓰거나 덮어쓰면 안 된다.

### 주간 티어 스냅샷

_Entity_

`WeeklyTier`는 완료된 주간 기록을 기준으로 확정된 티어 스냅샷이다.

#### 속성

- `userId`: 스냅샷 대상 사용자
- `weekStartDate`: 스냅샷 기준 주차
- `tierCode`: 확정된 티어 코드
- `tierScore`: 확정 당시 주간 점수. 표시 점수에 100을 곱한 정수로 저장한다.

#### 확정 기준

기본 확정 스케줄은 월요일 00:10, `Asia/Seoul` 기준이다.

```text
currentWeekStart
-> sourceWeekStart = currentWeekStart - N weeks
-> snapshotWeekStart = sourceWeekStart + 1 week
-> weekly_user_stats에서 run_count > 0이고 탈퇴하지 않은 사용자 조회
-> tier_score로 tierCode 해석
-> weekly_tiers upsert
```

#### 규칙

- 완료되지 않은 현재 주차는 스냅샷 확정 대상이 아니다.
- 같은 사용자와 같은 주차에는 하나의 스냅샷만 존재한다.
- 확정 작업은 재실행될 수 있어야 하며, 같은 값이면 불필요한 변경을 만들지 않는다.
- `weekly_tiers.tier_score`는 조회 시 소수점 둘째 자리 표시 점수로 되돌려 해석한다.

### 현재 표시 티어

_Display Policy_

현재 표시 티어는 사용자의 최신 확정 스냅샷이다.

#### 규칙

- 현재 주차의 실시간 기록이나 `weekly_user_stats`만으로 현재 표시 티어를 바꾸면 안 된다.
- 현재 표시 티어는 현재 주차 시작일 이하의 최신 `weekly_tiers` row를 기준으로 한다.
- 월요일 확정 배치가 지연되면 이전에 확정된 티어가 계속 표시될 수 있다.
- 스냅샷이 없을 때의 fallback은 호출자가 결정한다.
- record summary와 ranking은 스냅샷이 없으면 `TURTLE / B`를 기본 표시값으로 사용한다.
- `GET /weekly/tiers/current`는 스냅샷이 없으면 기존 API 동작대로 `null`을 반환할 수 있다.

### 온보딩 예상 티어

_Preview Result_

온보딩 예상 티어는 사용자가 입력한 거리와 페이스로 기록 점수 1건을 계산한 미리보기다.

#### 규칙

- 예상 티어는 실제 러닝 기록을 만들지 않는다.
- 예상 티어는 `weekly_user_stats`를 만들거나 갱신하지 않는다.
- 예상 티어는 `weekly_tiers` 스냅샷을 만들거나 갱신하지 않는다.
- 실제 현재 표시 티어는 주간 티어 스냅샷으로만 바뀐다.

## 도메인 규칙

- 기록 점수 산식은 도메인 전역에서 하나의 기준이어야 한다.
- 티어 구간 해석은 `tier_grade` 메타데이터를 기준으로 해야 한다.
- 현재 표시 티어는 확정 스냅샷 기준이다.
- 현재 주차 기록이 좋아졌다는 이유만으로 현재 표시 티어가 즉시 오르면 안 된다.
- `TURTLE / B`는 스냅샷이 없는 사용자를 위한 기본 표시값으로 쓰일 수 있지만, 모든 API가 반드시 같은 null/fallback 응답을 갖는 것은 아니다.
- 기본 프로필 이미지는 티어 메타데이터와 연결된 표시값이다. 사용자 커스텀 프로필 이미지 정책과 혼동하면 안 된다.

## 타 도메인과의 관계

| 도메인 | 관계 |
| --- | --- |
| Record | 기록 점수 계산에 필요한 거리와 페이스를 제공하고, `weekly_user_stats`에 주간 점수를 누적한다. |
| Ranking | 랭킹 응답에 표시할 현재 티어를 요청한다. Ranking은 티어 구간을 직접 해석하지 않는다. |
| Onboarding | 예상 티어와 기본 프로필 표시를 위해 점수 계산과 티어 메타데이터를 사용한다. 예상 티어는 실제 티어 상태를 변경하지 않는다. |
| User | 온보딩 완료 여부와 커스텀 프로필 이미지를 제공한다. Tier는 사용자 프로필 저장 정책을 소유하지 않는다. |

## 구현 앵커

### API

- `GET /weekly/tiers/current`
- `POST /onboarding/estimate-tier`

### 코드

- [TierScoreCalculator](../../src/main/java/com/ohgiraffers/dalryeo/tier/service/TierScoreCalculator.java)
- [TierMetadataCache](../../src/main/java/com/ohgiraffers/dalryeo/tier/service/TierMetadataCache.java)
- [TierService](../../src/main/java/com/ohgiraffers/dalryeo/tier/service/TierService.java)
- [CurrentWeeklyTierResolver](../../src/main/java/com/ohgiraffers/dalryeo/tier/service/CurrentWeeklyTierResolver.java)
- [TierGrade](../../src/main/java/com/ohgiraffers/dalryeo/tier/entity/TierGrade.java)
- [WeeklyTier](../../src/main/java/com/ohgiraffers/dalryeo/weeklytier/entity/WeeklyTier.java)
- [WeeklyTierService](../../src/main/java/com/ohgiraffers/dalryeo/weeklytier/service/WeeklyTierService.java)
- [WeeklyTierFinalizationService](../../src/main/java/com/ohgiraffers/dalryeo/weeklytier/service/WeeklyTierFinalizationService.java)
- [WeeklyTierFinalizationTransactionService](../../src/main/java/com/ohgiraffers/dalryeo/weeklytier/service/WeeklyTierFinalizationTransactionService.java)

### 대표 테스트

- [TierScoreCalculatorTest](../../src/test/java/com/ohgiraffers/dalryeo/tier/service/TierScoreCalculatorTest.java)
- [TierMetadataCacheTest](../../src/test/java/com/ohgiraffers/dalryeo/tier/service/TierMetadataCacheTest.java)
- [TierServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/tier/service/TierServiceTest.java)
- [CurrentWeeklyTierResolverTest](../../src/test/java/com/ohgiraffers/dalryeo/tier/service/CurrentWeeklyTierResolverTest.java)
- [WeeklyTierServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/weeklytier/service/WeeklyTierServiceTest.java)
- [WeeklyTierFinalizationServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/weeklytier/service/WeeklyTierFinalizationServiceTest.java)
- [WeeklyTierFinalizationTransactionServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/weeklytier/service/WeeklyTierFinalizationTransactionServiceTest.java)

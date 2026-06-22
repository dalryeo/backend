# 러닝 기록 시간 필드 OffsetDateTime 계약 전환

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

관련 이슈: #19

## GitHub 이슈 정리

이슈 제목은 아래처럼 정리합니다.

```text
러닝 기록 저장 시간 필드 OffsetDateTime 계약 전환
```

이슈 라벨은 `enhancement`가 적절합니다.
기존 기능이 완전히 잘못 동작한다기보다,
서버와 클라이언트의 시간 해석 기준을 명확하게 만드는 API 계약 개선 작업이기 때문입니다.

## 배경

러닝 기록 저장 API는 현재 `startAt`, `endAt`을 `LocalDateTime`으로 받습니다.

```java
private LocalDateTime startAt;
private LocalDateTime endAt;
```

요청 포맷은 아래와 같습니다.

```json
{
  "startAt": "2026-04-14T12:13:09",
  "endAt": "2026-04-14T12:14:12"
}
```

문제는 이 값에 시간대 정보가 없다는 점입니다.

```text
2026-04-14T12:13:09
-> 12시 13분 9초인 것은 알 수 있음
-> 한국 시간인지 UTC인지 서버 로컬 시간인지는 알 수 없음
```

`LocalDateTime`은 "날짜 + 시간"만 표현하고,
offset이나 timezone을 표현하지 않습니다.

## 실제로 발생한 문제

클라이언트가 한국 시간 기준으로 아래 기록을 보냈습니다.

```json
{
  "platform": "IOS",
  "distanceKm": 0.22,
  "durationSec": 63,
  "avgPaceSecPerKm": 234,
  "avgHeartRate": 105,
  "caloriesKcal": 16,
  "startAt": "2026-04-14T12:13:09",
  "endAt": "2026-04-14T12:14:12"
}
```

사용자 입장에서는 미래 시간이 아니지만,
서버가 UTC 기준 환경에서 동작하면 서버의 현재 시각은 한국 시간보다 9시간 느립니다.

예를 들어 아래와 같은 상황이 됩니다.

```text
한국 시간 현재: 2026-04-14 14:01
UTC 현재:      2026-04-14 05:01
```

서버가 `LocalDateTime.now()`를 UTC 기준 로컬 시간으로 해석하면,
요청의 `12:14`는 서버 현재 시간 `05:01`보다 훨씬 미래로 보일 수 있습니다.

```text
서버 기준 현재 시간: 05:01
허용 미래 시간: 05:06
요청 endAt: 12:14
-> FUTURE_RECORD_NOT_ALLOWED
```

즉 실제 문제가 되는 것은 "기록이 진짜 미래인가"가 아니라,
서버와 클라이언트가 같은 문자열 시간을 서로 다른 시간대로 해석하는 것입니다.

## 왜 OffsetDateTime이 필요한가

프론트가 offset이 포함된 ISO-8601 문자열을 보내면,
서버는 해당 시간이 어느 시간대 기준인지 알 수 있습니다.

변경 후 요청 예시는 아래와 같습니다.

```json
{
  "startAt": "2026-04-14T12:13:09+09:00",
  "endAt": "2026-04-14T12:14:12+09:00"
}
```

`+09:00`은 이 시간이 UTC보다 9시간 빠른 시간대,
즉 한국 시간 기준이라는 뜻입니다.

같은 시각을 UTC로 보내도 됩니다.

```json
{
  "startAt": "2026-04-14T03:13:09Z",
  "endAt": "2026-04-14T03:14:12Z"
}
```

아래 두 값은 같은 실제 시점입니다.

```text
2026-04-14T12:13:09+09:00
= 2026-04-14T03:13:09Z
```

따라서 API 계약은 아래처럼 바꾸는 것이 적절합니다.

```text
기존:
startAt/endAt은 timezone offset 없는 LocalDateTime 문자열

변경:
startAt/endAt은 timezone offset이 포함된 OffsetDateTime 문자열
```

## 시간대 정보가 필요한 요청 범위

현재 요청 DTO 기준으로 시간대 정보가 필요한 필드는 러닝 기록 저장 API의 `startAt`, `endAt`입니다.

```text
POST /records
startAt
endAt
```

반면 아래 필드는 시간대가 필요하지 않습니다.

```text
OnboardingRequest.birth
ProfileUpdateRequest.birth
```

생년월일은 특정 시각이 아니라 날짜 자체가 의미이므로 `LocalDate`가 맞습니다.

```json
{
  "birth": "1998-03-12"
}
```

## 구현 방향

현재 단계에서는 DB 컬럼까지 바로 `timestamptz`로 바꾸기보다,
API 요청 계약을 먼저 `OffsetDateTime`으로 바꾸고
DB에는 서비스 기준 시간대인 `Asia/Seoul`의 `LocalDateTime`으로 저장하는 방식을 추천합니다.

```text
프론트:
Offset 포함 시간 전송

백엔드 요청 DTO:
OffsetDateTime으로 수신

검증:
Instant 기준으로 미래 여부 비교

저장:
Asia/Seoul 기준 LocalDateTime으로 변환 후 기존 running_records.start_at/end_at에 저장
```

이 방식의 장점은 아래와 같습니다.

- API 요청 시간이 어느 시간대 기준인지 명확해집니다.
- 서버가 UTC 환경에서 떠 있어도 미래 시간 검증이 흔들리지 않습니다.
- 기존 DB 스키마 변경을 최소화할 수 있습니다.
- 기존 주간 집계와 기록 조회 로직의 변경 범위를 줄일 수 있습니다.

단점도 있습니다.

- DB에는 offset 정보 자체가 저장되지 않습니다.
- 서비스 기준 시간대가 `Asia/Seoul`이라는 정책을 명확히 해야 합니다.
- 장기적으로 글로벌 시간대 대응이 필요하면 DB를 `timestamptz`로 바꾸는 별도 작업이 필요합니다.

## 예상 코드 변경

`RunningRecordRequest`는 아래처럼 변경합니다.

```java
private OffsetDateTime startAt;
private OffsetDateTime endAt;
```

미래 시간 검증은 `Instant` 기준으로 수행합니다.

```java
Instant maxAllowedTime = Instant.now().plusSeconds(300);

if (request.getStartAt().toInstant().isAfter(maxAllowedTime)
        || request.getEndAt().toInstant().isAfter(maxAllowedTime)) {
    throw new RecordValidationException(RecordValidationErrorCode.FUTURE_RECORD_NOT_ALLOWED);
}
```

저장 직전에는 서비스 기준 시간대로 변환합니다.

```java
private LocalDateTime toServiceLocalDateTime(OffsetDateTime dateTime) {
    return dateTime
            .atZoneSameInstant(ZoneId.of("Asia/Seoul"))
            .toLocalDateTime();
}
```

이렇게 하면 프론트가 `Z`로 보내든 `+09:00`으로 보내든,
서버는 같은 실제 시점을 기준으로 검증하고 저장할 수 있습니다.

## 이번 이슈에서 하지 않을 것

이번 이슈는 시간대 계약을 명확히 하는 작업입니다.
아래 내용은 별도 이슈로 분리합니다.

- 짧은 거리 기록의 평균 페이스 허용 오차 완화
- 서버가 요청값을 계산해서 보정 저장하는 방식 도입
- DB 컬럼을 `timestamptz`로 변경
- 모든 응답 DTO를 offset 포함 시간으로 전환
- JSON 파싱 실패 공통 예외 응답 개선
- 서비스 기준 시간대와 주차 계산 로직 공통화

특히 `avgPaceSecPerKm` 검증 문제는 시간대 문제와 별개입니다.

```text
시간대 문제:
실제 과거 기록이 미래 기록으로 오판되는 문제

평균 페이스 문제:
distanceKm, durationSec, avgPaceSecPerKm 계산 기준이 서로 맞지 않는 문제
```

두 문제를 한 이슈에서 같이 처리하면 원인이 흐려지므로 분리하는 것이 좋습니다.

## PR 리뷰 후속 판단

### 1. 서비스 시간 정책 공통화

리뷰에서 날짜 계산 로직과 `SERVICE_ZONE_ID` 같은 상수를 공통 유틸리티나 서비스로 중앙화하라는 의견이 있었습니다.

이 의견은 타당합니다.

현재 서비스에는 아래 기준이 여러 곳에 흩어져 있습니다.

- 서비스 기준 시간대: `Asia/Seoul`
- 현재 주차 시작일: 월요일
- 기록 시간의 서비스 기준 `LocalDateTime` 변환

예를 들면 아래와 같은 계산이 여러 서비스에서 반복됩니다.

```java
LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
```

이 기준이 기능마다 달라지면 같은 러닝 기록이어도
랭킹, 주간 요약, 현재 티어, 주간 집계에서 서로 다른 주차로 해석될 수 있습니다.

따라서 장기적으로는 아래처럼 공통 정책으로 분리하는 것이 맞습니다.

```java
public final class TimePolicy {

    public static final ZoneId SERVICE_ZONE_ID = ZoneId.of("Asia/Seoul");
    public static final DayOfWeek WEEK_START_DAY = DayOfWeek.MONDAY;

    private TimePolicy() {
    }

    public static LocalDate currentWeekStart() {
        return weekStart(LocalDate.now(SERVICE_ZONE_ID));
    }

    public static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(WEEK_START_DAY));
    }

    public static LocalDateTime toServiceLocalDateTime(OffsetDateTime dateTime) {
        return dateTime.atZoneSameInstant(SERVICE_ZONE_ID)
                .toLocalDateTime();
    }
}
```

다만 이 작업은 이번 #19 PR에 바로 넣지 않는 것이 좋습니다.

이유는 영향 범위가 `RecordService`에만 머물지 않기 때문입니다.

- `RecordService`
- `RankingService`
- `WeeklyTierService`
- `CurrentTierResolver`
- `WeeklyUserStatsService`

위 서비스들은 주간 요약, 랭킹, 현재 티어, 집계 테이블과 연결되어 있습니다.
시간 정책 공통화는 좋은 리팩터링이지만,
이번 PR의 핵심인 `startAt/endAt` API 계약 변경과 섞으면 리뷰 범위가 커지고 회귀 위험이 생깁니다.

따라서 이번 PR에서는 offset 계약과 `Instant` 기준 검증까지만 처리하고,
시간 정책 중앙화는 별도 리팩터링 이슈로 분리합니다.

### 2. JSON 파싱 실패 응답 개선

리뷰에서 `HttpMessageNotReadableException` 발생 시
단순히 `"요청 본문을 읽을 수 없습니다."`만 반환하지 말고
필드별 오류 정보를 제공하라는 의견이 있었습니다.

이 의견도 타당합니다.

예를 들어 프론트가 아래처럼 offset 없는 값을 보내면
`OffsetDateTime`으로 변환할 수 없어 컨트롤러 진입 전에 Spring/Jackson 단계에서 실패합니다.

```json
{
  "startAt": "2026-04-14T12:13:09"
}
```

이때 단순 메시지만 내려주면 클라이언트는 어떤 필드를 고쳐야 하는지 알기 어렵습니다.

다만 이 예외는 러닝 기록 전용 예외가 아닙니다.
모든 API의 JSON 파싱 실패와 타입 변환 실패에 적용되는 전역 공통 예외입니다.

따라서 #19 PR 안에서 `startAt/endAt` 전용으로 빠르게 처리하면
공통 예외 응답 정책이 특정 도메인에 종속될 수 있습니다.

이번 PR에서는 아래까지만 보장합니다.

- offset 없는 `startAt/endAt` 요청은 저장하지 않고 `400 BAD_REQUEST`로 거부한다.
- 서버는 offset이 포함된 값을 `Instant` 기준으로 검증한다.

필드별 오류 정보를 포함한 공통 예외 응답 개선은 별도 이슈로 분리합니다.

후속 문서:

```text
docs/archive/common-exception/json-parse-error-response-improvement.md
```

## 완료 기준

- `RunningRecordRequest.startAt/endAt`을 `OffsetDateTime`으로 변경합니다.
- 요청 포맷은 offset 포함 ISO-8601 문자열로 변경합니다.
- 미래 시간 검증은 `Instant` 기준으로 수행합니다.
- 저장 시 `Asia/Seoul` 기준 `LocalDateTime`으로 변환합니다.
- `+09:00` 요청과 `Z` 요청이 같은 실제 시점으로 처리되는지 테스트합니다.
- offset 없는 기존 요청이 실패하거나 명확한 API 계약 위반으로 처리되는지 확인합니다.
- 기존 기록 저장 성공/실패 응답 계약을 다시 검증합니다.

## 프론트 전달 문구

프론트에는 아래처럼 전달하면 됩니다.

```text
러닝 기록 저장 API의 startAt/endAt은 timezone offset이 포함된 ISO-8601 문자열로 보내주세요.

예:
2026-04-14T12:13:09+09:00
또는
2026-04-14T03:13:09Z

기존처럼 2026-04-14T12:13:09 형태로 보내면 서버가 시간대를 알 수 없어
미래 시간 검증이 잘못될 수 있습니다.
```

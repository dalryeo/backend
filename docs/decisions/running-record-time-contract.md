# Running Record Time Contract

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-20

## 결정

러닝 기록 저장 API의 `startAt`, `endAt`은 offset이 포함된 `OffsetDateTime` 계약으로 받는다.

서버는 요청 시각을 `Instant` 기준으로 검증하고, 저장 직전에는 서비스 기준 시간대의 `LocalDateTime`으로 변환한다. 현재 서비스 기준 시간대는 `Asia/Seoul`이다.

## 배경

`LocalDateTime` 요청은 시간대 정보가 없다. 클라이언트가 한국 시간 기준으로 보낸 값을 서버가 UTC 환경에서 해석하면 실제로는 과거 기록인데도 미래 기록처럼 보일 수 있다.

예를 들어 `2026-04-14T12:13:09`만으로는 한국 시간인지, UTC인지, 서버 로컬 시간인지 알 수 없다. 러닝 기록은 미래 기록 검증, 주간 집계, 랭킹에 직접 영향을 주므로 API 계약에서 시간대 해석을 명확히 해야 한다.

## 선택한 방식

요청 예시는 아래와 같다.

```json
{
  "startAt": "2026-04-14T12:13:09+09:00",
  "endAt": "2026-04-14T12:14:12+09:00"
}
```

같은 실제 시각이면 UTC `Z` 형식도 허용한다.

```json
{
  "startAt": "2026-04-14T03:13:09Z",
  "endAt": "2026-04-14T03:14:12Z"
}
```

서버 저장 기준은 아래 흐름을 따른다.

```text
요청 DTO: OffsetDateTime
검증: Instant 기준
저장: app.time-zone 기준 LocalDateTime
DB: timestamp
```

## 주요 판단

- 요청에 offset이 없으면 기록 시각의 실제 기준을 알 수 없으므로 저장 계약으로 보지 않는다.
- 미래 시간 검증은 서버 로컬 `LocalDateTime.now()`가 아니라 `Instant` 기준으로 판단한다.
- DB에는 현재 offset 자체를 저장하지 않는다.
- 저장된 `running_records.start_at`, `running_records.end_at`은 서비스 시간대 기준 local timestamp로 해석한다.
- 글로벌 시간대 대응이나 원본 offset 보존이 필요해지면 DB 타입과 저장 모델을 별도 결정으로 다룬다.

## 영향 범위

이 결정은 `POST /records`의 `startAt`, `endAt`에 적용된다.

생년월일 같은 날짜 필드는 특정 시각이 아니라 날짜 자체가 의미이므로 `LocalDate`를 유지한다.

주간 기준일, 랭킹 현재 주차, 주간 티어 확정 기준은 `docs/standards/time-policy.md`를 따른다.

## 구현 앵커

- `src/main/java/com/ohgiraffers/dalryeo/record/dto/RunningRecordRequest.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/service/RecordService.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/service/RunningRecordValidator.java`
- `src/main/java/com/ohgiraffers/dalryeo/common/time/ServiceDateProvider.java`
- `src/main/resources/db/migration/V1__init.sql`
- `src/test/java/com/ohgiraffers/dalryeo/record/service/RunningRecordValidatorTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/record/service/RecordServiceTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/common/exception/GlobalExceptionHandlerTest.java`

## 보관된 원문

- `docs/archive/records/running-record-time-contract-original.md`

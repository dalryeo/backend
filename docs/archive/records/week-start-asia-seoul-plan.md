# 주간 집계와 랭킹 기준일 Asia/Seoul 통일

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

관련 이슈: #33

권장 브랜치: `fix/seoul-week-boundary`

## 배경

기록 저장 시 `OffsetDateTime`은 Asia/Seoul 기준 `LocalDateTime`으로 변환된다. 하지만 주간 요약, 랭킹, 주간 티어 조회는 여러 서비스에서 `LocalDate.now()`를 직접 사용한다.

서버 JVM 타임존이 UTC인 경우, 한국 시간 월요일 00:00 전후에 주간 경계가 어긋날 수 있다. 러닝 기록 서비스에서 주간 집계는 사용자 경험과 랭킹 신뢰도에 직접 영향을 주므로 런칭 전 통일이 필요하다.

## 목표

- 모든 “오늘”, “이번 주 시작일” 계산은 Asia/Seoul 기준이어야 한다.
- JVM 기본 타임존과 무관하게 동일한 결과를 반환해야 한다.
- 테스트에서는 시간을 고정해서 월요일 경계를 검증할 수 있어야 한다.

## 제외 범위

- DB 컬럼 타입을 `TIMESTAMP WITH TIME ZONE`으로 전환
- API 응답의 날짜/시간 계약 변경
- 과거 저장 데이터 보정
- 주 시작일 정책 변경

현재 정책은 월요일 시작 주차를 유지한다.

## 설계 방향

공통 provider를 둔다.

```text
ServiceClock 또는 DateTimeProvider
- nowInstant()
- todayInServiceZone()
- currentWeekStart()
- toServiceLocalDateTime(OffsetDateTime)
```

이미 `Clock` bean이 있으므로 이를 활용하되, 서비스 기준 zone은 `Asia/Seoul`로 명시한다. `LocalDate.now()`와 `LocalDateTime.now()` 직접 호출을 줄이고, 테스트에서 `Clock.fixed()`를 주입할 수 있게 한다.

## 주요 수정 파일

- `src/main/java/com/ohgiraffers/dalryeo/config/ClockConfig.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/service/RecordService.java`
- `src/main/java/com/ohgiraffers/dalryeo/ranking/service/RankingService.java`
- `src/main/java/com/ohgiraffers/dalryeo/weeklytier/service/WeeklyTierService.java`
- `src/main/java/com/ohgiraffers/dalryeo/record/outbox/*`
- 날짜/시간 관련 테스트

## 실행 체크리스트

- [ ] 현재 `LocalDate.now()`와 `LocalDateTime.now()` 사용처를 전수 확인한다.
- [ ] 서비스 기준 zone 상수를 한 곳으로 모은다.
- [ ] 주간 시작일 계산을 공통 메서드로 분리한다.
- [ ] `RecordService`의 current week 계산을 교체한다.
- [ ] `RankingService`의 current week 계산을 교체한다.
- [ ] `WeeklyTierService`의 current week 계산을 교체한다.
- [ ] outbox retry 시간은 운영 기준이 UTC인지 local time인지 별도 확인하고 필요한 경우만 조정한다.
- [ ] KST 월요일 00:00 경계 테스트를 추가한다.

## 검증 계획

우선 실행:

```bash
./gradlew test --tests '*RecordServiceTest' --tests '*RankingServiceTest' --tests '*WeeklyTierServiceTest' --tests '*ApiContractIntegrationTest'
```

통합 회귀:

```bash
./gradlew test --rerun-tasks
```

경계 테스트 시나리오:

```text
UTC 일요일 15:00 == KST 월요일 00:00
UTC 일요일 14:59:59 == KST 일요일 23:59:59
두 시점에서 currentWeekStart 결과가 KST 기준으로 달라지는지 확인
```

## 롤백 기준

배포 후 주간 요약이나 랭킹이 비어 보이면, 기록 저장 시각과 조회 주차 계산이 같은 zone을 쓰는지 먼저 확인한다. zone provider 변경을 롤백하기 전에 테스트 fixture의 기준 시각과 운영 JVM time zone을 확인한다.

## 운영 확인

- 월요일 00:00 KST 전후 신규 기록의 주차
- `/records/summary`
- `/weekly/summary/current`
- `/ranking/weekly/score`
- `/ranking/me`

## 진행 기록

| 날짜 | 상태 | 기록 |
| --- | --- | --- |
| 2026-05-06 | 설계 | #33은 기록, 랭킹, 주간 티어에 걸친 동작 변경이므로 단독 브랜치로 처리하기로 결정했다. |

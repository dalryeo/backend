# 2026-06-13 중간 규모 대응 개선 항목

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

## 목적

전체 코드리뷰와 2026-06-13 부하테스트 결과를 바탕으로 중간 규모 대응 전에 개선해야 할 항목을 정리한다.

관련 기준 문서:

- `docs/archive/performance/loadtest-baseline-2026-06-13.md`

## 중간 규모 가정

- DAU: 3만
- 피크 API 트래픽: 150 RPS
- 기록 저장 피크: 20~50 RPS
- 랭킹/요약 조회 피크: 80~120 RPS
- 앱 replica: 2~3개
- DB: PostgreSQL managed DB 또는 DB 앞단 pooler 사용

## 결론

현재 구조는 초기 런칭 규모에서는 동작 가능하지만, 중간 규모로 올리기 전에 다음 세 영역을 먼저 안정화해야 한다.

1. DB session 한도와 앱 replica/Hikari/Flyway startup 구조
2. 기록 저장 후 outbox 집계 처리량
3. 모바일 재시도에 대한 기록 저장 idempotency

부하테스트에서 저장 API 자체는 TestDB 기준 30 RPS까지 안정적이었다. 그러나 운영 기본 outbox 설정에서는 10 RPS 쓰기 30초만으로 outbox backlog가 남았다. 운영 장애 로그도 비즈니스 로직 오류가 아니라 DB session-mode client 한도 초과로 인한 startup 실패였다.

## P0: 즉시 안정화 항목

### 1. DB session 한도 기준으로 Hikari와 replica 수 재산정

근거:

- 운영 에러 로그에 `EMAXCONNSESSION max clients reached in session mode`가 기록됐다.
- 앱 시작 중 `flywayInitializer`가 DB connection을 얻지 못해 ApplicationContext가 실패했다.
- 현재 기본 Hikari max pool size는 replica당 3이다.
- Container App replica, active revision, Flyway startup connection까지 모두 DB session 한도에 영향을 준다.

개선 방향:

- DB 또는 pooler의 실제 max client/session 한도를 확인한다.
- Container App의 max replica와 active revision 수를 확인한다.
- 다음 기준으로 Hikari max pool size를 계산한다.

```text
앱 replica당 Hikari max pool size
= floor((DB session 한도 - 운영 예약분 - migration/job 예약분) / 동시 active replica 수)
```

초기 권장:

- dev 또는 작은 DB 환경: max replica 1, `DB_HIKARI_MAXIMUM_POOL_SIZE=1~2`
- 운영 중간 규모: DB session 한도 확인 후 replica와 pool size를 같이 산정

성공 기준:

- 배포 또는 재시작 중 `flywayInitializer` connection 실패가 발생하지 않는다.
- Hikari connection timeout 로그가 사라지거나 운영 허용 범위 안으로 줄어든다.
- Container App scale-out 중에도 새 replica가 정상 기동한다.

사용자 결정 필요:

- `api.dalryeo.store`가 dev 리소스를 바라보는 것이 의도인지 확인
- 환경별 DB session 한도 제공
- 환경별 Container App max replica 목표 결정

### 2. Flyway 실행 위치 분리 검토

근거:

- 이번 장애는 앱 시작 시 Flyway가 connection을 얻지 못해 앱 전체가 뜨지 못한 케이스다.
- replica가 여러 개 동시에 뜨면 모든 replica가 startup 과정에서 migration 확인 connection을 요구한다.

개선 방향:

- 단기: 앱 startup Flyway는 유지하되 DB session 여유를 확보한다.
- 중기: 배포 workflow에서 앱 배포 전 migration job을 한 번 실행하고, 앱 컨테이너는 `spring.flyway.enabled=false`로 기동하는 구조를 검토한다.
- migration job에도 별도 DB connection 예산을 배정한다.

성공 기준:

- 앱 replica startup이 migration connection 경합 때문에 실패하지 않는다.
- migration 실패와 앱 runtime 장애를 분리해서 관측할 수 있다.

사용자 결정 필요:

- Flyway를 앱 startup에 계속 둘지, 배포 전 migration job으로 분리할지
- 운영 DB migration 권한을 어떤 실행 주체에 줄지

### 3. dev/prod 배포 환경 명명 정리

근거:

- GitHub Actions workflow 이름과 Container App 이름은 `backend-api-dev`이다.
- 같은 workflow에서 `SENTRY_ENVIRONMENT=prod`가 설정된다.
- 장애 알림도 `dalryeo-dev-response-time-alert`로 들어왔다.

개선 방향:

- dev, staging, prod 리소스 이름과 alert 이름을 분리한다.
- dev workflow는 `SENTRY_ENVIRONMENT=dev`를 사용한다.
- prod workflow가 따로 있다면 prod workflow에서만 `SENTRY_ENVIRONMENT=prod`를 사용한다.
- `api.dalryeo.store`가 어느 Container App을 바라보는지 DNS/ingress 기준으로 확인한다.

성공 기준:

- 알림만 보고도 dev/prod 영향을 구분할 수 있다.
- prod 장애와 dev 장애가 같은 Sentry environment로 섞이지 않는다.

사용자 결정 필요:

- 현재 `api.dalryeo.store`의 대상 환경 확정
- dev/prod 도메인 분리 여부 결정

## P1: 기록 저장과 집계 정합성 개선

### 4. outbox 처리량 상향

근거:

운영 기본 outbox 설정 재현 테스트:

- 설정: `fixed-delay-ms=30000`, `batch-size=20`
- 부하: 10 RPS 쓰기, 30초
- 저장 결과: 290건 전부 200
- 직후 outbox: `DONE 40`, `PENDING 250`
- 직후 `weekly_user_stats.run_count`: 40

즉 저장 API는 성공하지만 주간 집계와 랭킹 반영이 저장 속도를 따라가지 못한다.

개선 방향:

- 단기 운영값:

```yaml
record:
  outbox:
    scheduler:
      fixed-delay-ms: 1000
      batch-size: 100
```

- 중기 코드 개선:
  - `LIMIT 1` 반복 claim 대신 batch claim으로 변경한다.
  - claim query 정렬을 pending retry index와 맞춘다.
  - outbox backlog, failed count, oldest pending age metric을 추가한다.
  - API pod와 outbox worker를 분리할 수 있는 설정을 준비한다.

성공 기준:

- 30 RPS 쓰기 60초 후 outbox backlog가 장시간 누적되지 않는다.
- 기록 저장 후 주간 집계 반영 지연이 운영 허용 범위 안에 들어온다.
- failed outbox event가 알림으로 드러난다.

사용자 결정 필요:

- 기록 저장 후 랭킹/요약 반영 허용 지연 시간
  - 예: 5초 이내, 30초 이내, 1분 이내
- outbox worker를 API pod와 분리할지 여부

### 5. 기록 저장 idempotency key 도입

근거:

- 현재 `POST /records`는 요청마다 새 `running_records` row를 만든다.
- 모바일 네트워크 timeout, 재시도, 중복 전송이 발생하면 같은 러닝 기록이 여러 번 저장된다.
- outbox는 저장된 record 기준으로 집계하므로 중복 저장은 `weekly_user_stats`와 랭킹을 직접 부풀린다.
- 부하 상황에서는 timeout/retry가 더 자주 발생할 수 있다.

개선 방향:

- 요청 DTO에 client-generated idempotency key를 추가한다.
- 예: `clientRecordId`
- DB에 `client_record_id` 컬럼을 추가한다.
- `(user_id, source, client_record_id)` unique constraint를 추가한다.
- 중복 요청이면 기존 record id를 반환한다.

성공 기준:

- 같은 사용자, 같은 platform/source, 같은 client record id로 여러 번 요청해도 기록은 1건만 저장된다.
- 중복 요청이 weekly stats와 ranking을 중복 증가시키지 않는다.

사용자 결정 필요:

- 클라이언트가 안정적으로 보낼 수 있는 idempotency key 이름과 생성 방식
- 기존 클라이언트 하위 호환 기간

### 6. outbox 재처리와 집계 재계산 운영 도구

근거:

- outbox backlog 또는 실패가 생기면 `weekly_user_stats`와 실제 `running_records`가 일시적으로 어긋난다.
- 현재 API에는 기록 삭제/집계 차감 기능이 없다.
- 부하테스트 cleanup도 테스트 계정 삭제와 FK cascade에 의존했다.

개선 방향:

- 특정 사용자/주차의 `weekly_user_stats`를 `running_records`에서 재계산하는 운영용 service 또는 SQL runbook을 만든다.
- `FAILED` outbox event 재시도 절차를 runbook으로 남긴다.
- 테스트 계정 cleanup SQL을 운영 runbook과 분리해 문서화한다.

성공 기준:

- 집계 불일치가 발생했을 때 수동 복구 절차가 있다.
- 복구 후 running records와 weekly stats의 값이 일치한다.

사용자 결정 필요:

- 운영 복구를 API/admin 기능으로 만들지, SQL runbook으로 둘지

## P2: 조회 성능과 도메인 계약 정리

### 7. 랭킹 조회 성능 재검증

근거:

- 운영 공개 랭킹은 10 RPS read-only에서 안정적이었다.
- 운영 30 RPS read-only에서는 429와 timeout이 발생했다.
- TestDB read mix 30 RPS는 p95 31ms로 빠르게 통과했다.
- TestDB 데이터 규모가 작아 `/ranking/me` count-ahead 쿼리의 중간 규모 병목 가능성은 아직 검증되지 않았다.

개선 방향:

- 3만~5만 주간 활성 사용자 규모의 테스트 데이터를 만든다.
- `/ranking/me`의 `countAheadForScoreRank`, `countAheadForDistanceRank` 쿼리에 대해 `EXPLAIN ANALYZE`를 남긴다.
- 필요하면 주간 랭킹 position을 precompute하거나 cache한다.
- 공개 랭킹 API는 앞단 rate limit 정책을 확인한다.

성공 기준:

- 3만~5만 weekly stats row에서 `/ranking/me` p95가 목표 안에 들어온다.
- 공개 랭킹 30 RPS 이상 테스트에서 429가 의도된 정책인지 확인된다.

사용자 결정 필요:

- 공개 랭킹 API의 허용 RPS와 rate limit 정책
- 랭킹 실시간성 요구 수준

### 8. 티어 메타데이터 조회 캐싱

근거:

- 랭킹 응답 변환 시 row마다 `TierService.resolveByScore()`를 호출한다.
- 티어/등급 메타데이터는 작은 정적 데이터에 가깝다.
- 현재는 DB 조회가 반복될 수 있다.

개선 방향:

- 앱 시작 시 tier, tier_grade를 메모리에 로드한다.
- score range resolution을 메모리에서 수행한다.
- metadata 변경이 필요한 경우 재기동 또는 refresh endpoint 정책을 정한다.

성공 기준:

- 랭킹 top 100 응답 생성 시 티어 메타데이터 DB 조회가 반복되지 않는다.

사용자 결정 필요:

- 티어 메타데이터를 운영 중 동적으로 바꿀 필요가 있는지

### 9. 주간 티어 스냅샷 writer 계약 정리

근거:

- `weekly_tiers`를 읽는 서비스는 남아 있다.
- 현재 코드 기준으로 `weekly_tiers` 생산 writer가 없다.
- 코드리뷰에서 확정 티어 스냅샷 정책과 현재 주간 live stats 정책이 섞여 있는 것으로 확인됐다.

개선 방향:

- 선택지 A: `weekly_tiers` 확정 스냅샷 정책 유지
  - 주간 finalization scheduler/job을 복구한다.
  - current tier는 최신 확정 snapshot에서만 읽는다.
- 선택지 B: `weekly_user_stats`를 단일 source of truth로 사용
  - `weekly_tiers` 의존을 제거한다.
  - current tier는 live weekly stats 기준으로 계산한다.

권장:

- 사용자에게 “티어는 주간 종료 후 확정된다”는 경험을 줄 계획이면 선택지 A가 맞다.
- 초기 런칭에서 단순성과 실시간성을 우선하면 선택지 B가 비용이 낮다.

성공 기준:

- current tier가 어떤 테이블과 시점 기준인지 코드와 API 응답이 일관된다.

사용자 결정 필요:

- 티어가 실시간 변경되는 UX인지, 주간 확정 후 변경되는 UX인지

### 10. 시간대 기준 통일

근거:

- 기록 저장은 `Asia/Seoul` 기준으로 변환한다.
- 여러 조회 서비스는 `LocalDate.now()`를 직접 사용한다.
- 월요일 자정 경계에서 current week 계산이 서버 timezone에 따라 달라질 수 있다.

개선 방향:

- `ServiceDateProvider` 또는 `Clock + ZoneId.of("Asia/Seoul")` 기준을 통일한다.
- `RecordService`, `RankingService`, `WeeklyTierService`, `AnalysisService`의 날짜 계산을 같은 기준으로 맞춘다.
- 월요일 00:00 KST 경계 테스트를 추가한다.

성공 기준:

- 서버 timezone이 UTC여도 주간 계산은 KST 기준으로 일관된다.

## P3: 운영 준비 보강

### 11. JWT secret fail-fast

근거:

- 현재 `JWT_SECRET`이 없으면 기본 sample secret으로 앱이 뜰 수 있다.
- 이는 부하 문제가 아니라 계정 보안 문제다.

개선 방향:

- 운영 profile에서는 기본 secret을 허용하지 않는다.
- `JWT_SECRET` 미설정 또는 sample 값이면 startup 실패 처리한다.
- 설정 테스트를 추가한다.

성공 기준:

- 운영에서 sample secret으로 앱이 기동되지 않는다.

### 12. 프로필 이미지 저장소 외부화

근거:

- 현재 custom profile image는 앱 로컬 파일시스템에 저장된다.
- replica 확장, 재시작, reschedule 시 파일 접근 불일치가 발생할 수 있다.

개선 방향:

- object storage와 CDN을 사용한다.
- 단기 대안으로 shared persistent volume을 검토할 수 있다.

성공 기준:

- 여러 replica에서 같은 profile image URL을 안정적으로 serving한다.

사용자 결정 필요:

- 사용할 object storage 서비스
- 이미지 공개 URL 정책

## 권장 실행 순서

### 1단계: 운영 안정화

1. `api.dalryeo.store` 대상 환경 확인
2. Container App replica/revision 상태 확인
3. DB/pooler session 한도 확인
4. Hikari pool size와 max replica 재산정
5. dev/prod Sentry environment 정리

### 2단계: outbox 개선

1. 운영 환경변수로 outbox `fixed-delay-ms`, `batch-size` 상향
2. outbox backlog metric 추가
3. batch claim 구조로 코드 개선
4. 쓰기 30 RPS TestDB 재검증

### 3단계: 데이터 정합성

1. 기록 저장 idempotency key 설계
2. DB migration 추가
3. 중복 요청 테스트 추가
4. 모바일 클라이언트 적용 계획 수립

### 4단계: 조회/도메인 정리

1. 중간 규모 weekly stats seed 데이터 생성
2. 랭킹 쿼리 `EXPLAIN ANALYZE`
3. 필요 시 ranking cache/precompute 설계
4. weekly tier 정책 확정
5. KST date provider 통일

## 이후 부하테스트 기준

개선 후에는 같은 시나리오를 반복한다.

필수 시나리오:

- TestDB read mix 30 RPS, 60초
- TestDB write 30 RPS, 60초
- 운영 기본 outbox 설정이 아닌 개선된 outbox 설정으로 backlog 확인
- 3만~5만 weekly stats row에서 `/ranking/me` read test

성공 기준 예시:

- read mix p95 100ms 이하
- write API p95 100ms 이하
- write 30 RPS 60초 후 outbox backlog가 1분 안에 0으로 수렴
- outbox failed event 0건
- DB connection timeout 0건

## 이번 문서의 범위 밖

- 실제 운영 DB schema 변경
- API 계약 변경 구현
- 클라이언트 idempotency key 적용
- Azure 리소스 직접 변경
- GitHub Actions workflow 수정

# 2026-06-13 부하테스트 기준선

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

## 목적

중간 규모 대응 개선 전에 현재 API와 비동기 집계 구조의 기준 성능을 남긴다.

이번 테스트는 두 범위로 나눠 진행했다.

- 운영 배포 API: 공개 랭킹 read-only만 낮은 강도로 확인
- TestDB + 로컬 앱: 인증 read API와 기록 저장 API, outbox 집계 반영 상태 확인

비밀값과 토큰 원문은 문서에 남기지 않는다.

## 중간 규모 가정

- DAU: 3만
- 피크 API 트래픽: 150 RPS
- 기록 저장 피크: 20~50 RPS
- 랭킹/요약 조회 피크: 80~120 RPS
- 앱 replica: 2~3개
- DB: PostgreSQL managed DB 또는 DB 앞단 pooler 사용

## 테스트 방식

이번 부하테스트는 별도 부하테스트 도구를 설치하지 않고, 로컬에서 Node.js 내장 `http`/`https` 모듈로 간단한 요청 생성 스크립트를 실행했다.

사용한 도구:

- Node.js: RPS 고정 요청 생성, 응답 코드와 latency 집계
- `/usr/bin/curl`: 단건 canary 확인
- `psql`: TestDB 테스트 계정 생성, outbox/집계 상태 확인, cleanup
- `./gradlew bootRun`: TestDB에 연결한 로컬 Spring Boot 앱 실행

사용하지 않은 도구:

- `hey`: 로컬에 설치되어 있지 않았다.
- `wrk`: 로컬에 설치되어 있지 않았다.
- `ab`: 설치되어 있었지만, 인증 헤더와 JSON write 시나리오를 같은 방식으로 제어하기 위해 사용하지 않았다.

공통 측정 방식:

- 요청별 시작/종료 시간을 Node.js에서 측정했다.
- 응답 코드는 status code별로 집계했다.
- latency는 성공/오류 여부와 별개로 HTTP 응답이 온 요청을 기준으로 p50, p95, p99, max를 계산했다.
- 요청 timeout은 5초로 두었다.
- 운영 API 요청에는 테스트용 User-Agent를 붙였다.

운영 API 테스트 방식:

- base URL: `https://api.dalryeo.store`
- 운영 환경 보호를 위해 공개 read-only API만 호출했다.
- 쓰기 테스트는 운영 API에서 실행하지 않았다.
- 처음 제공된 값은 refresh token 원문이 아니어서 access token 발급에 실패했다.

TestDB 테스트 방식:

- `.env.test`의 TestDB에 로컬 Spring Boot 앱을 연결했다.
- 테스트 사용자를 `loadtest_*` 닉네임으로 생성했다.
- 로컬 테스트용 JWT secret으로 access token을 생성했다.
- read mix는 로컬 앱의 인증/비인증 조회 API를 섞어서 호출했다.
- write 테스트는 테스트 사용자에게만 `POST /records`를 호출했다.
- 테스트 후 테스트 사용자 삭제로 FK cascade cleanup을 수행했고, 남은 사용자/기록/집계가 0건임을 확인했다.

## 운영 API read-only 테스트

대상 base URL은 `https://api.dalryeo.store`이다.

운영 환경이므로 쓰기 테스트는 진행하지 않았다. 처음 제공된 값은 refresh token 원문이 아니어서 `/auth/token/refresh`에서 `AC-006 refreshToken 유효하지 않음`이 반환됐다.

### 5 RPS, 30초

대상:

- `GET /ranking/weekly/score`
- `GET /ranking/weekly/distance`

결과:

| 항목 | 값 |
| --- | ---: |
| 요청 수 | 150 |
| 200 | 149 |
| timeout | 1 |
| p50 | 272ms |
| p95 | 754ms |
| p99 | 1733ms |

### 10 RPS, 60초

대상:

- `GET /ranking/weekly/score`
- `GET /ranking/weekly/distance`

결과:

| 항목 | 값 |
| --- | ---: |
| 요청 수 | 590 |
| 200 | 590 |
| p50 | 310ms |
| p95 | 559ms |
| p99 | 1296ms |
| max | 1879ms |

엔드포인트별 p95:

| 엔드포인트 | 요청 수 | p95 | max |
| --- | ---: | ---: | ---: |
| `/ranking/weekly/distance` | 295 | 561ms | 1572ms |
| `/ranking/weekly/score` | 295 | 559ms | 1879ms |

### 30 RPS, 120초

대상:

- `GET /ranking/weekly/score`
- `GET /ranking/weekly/distance`

결과:

| 항목 | 값 |
| --- | ---: |
| 요청 수 | 3570 |
| 200 | 1407 |
| 429 | 1975 |
| timeout | 188 |
| p50 | 67ms |
| p95 | 953ms |
| p99 | 2298ms |
| max | 4798ms |

엔드포인트별 결과:

| 엔드포인트 | 요청 수 | 200 | 오류 | p95 | p99 | max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `/ranking/weekly/distance` | 1785 | 696 | 1089 | 905ms | 1925ms | 4566ms |
| `/ranking/weekly/score` | 1785 | 711 | 1074 | 1064ms | 2494ms | 4798ms |

해석:

- 10 RPS read-only까지는 전부 200이었다.
- 30 RPS에서는 429가 다수 발생했다.
- 이는 앱 내부 처리 한계라기보다 앞단 rate limit 또는 보호 정책이 작동했을 가능성이 높다.
- 30 RPS 테스트 후 단건 확인에서 `/ranking/weekly/score`는 `200`, 306ms로 응답했다.

## TestDB + 로컬 앱 테스트

로컬 Spring Boot 앱을 TestDB에 연결해 실행했다.

공통 설정:

- 앱 포트: `18080`
- DB: `.env.test`의 TestDB
- 테스트 계정: `loadtest_*` 닉네임으로 생성
- access token: 로컬 테스트 secret으로 생성
- cleanup: 테스트 사용자 삭제 후 FK cascade로 기록, outbox, 주간 집계 삭제 확인

### Read Mix, 30 RPS, 60초

대상:

- `GET /ranking/weekly/score`
- `GET /ranking/weekly/distance`
- `GET /ranking/me`
- `GET /records/summary`
- `GET /weekly/summary/current`

결과:

| 항목 | 값 |
| --- | ---: |
| 요청 수 | 1770 |
| 200 | 1770 |
| p50 | 15ms |
| p95 | 31ms |
| p99 | 50ms |
| max | 78ms |

엔드포인트별 결과:

| 엔드포인트 | 요청 수 | p50 | p95 | p99 | max |
| --- | ---: | ---: | ---: | ---: | ---: |
| `/ranking/weekly/score` | 354 | 12ms | 28ms | 42ms | 77ms |
| `/ranking/weekly/distance` | 354 | 12ms | 28ms | 46ms | 76ms |
| `/records/summary` | 354 | 17ms | 34ms | 73ms | 78ms |
| `/weekly/summary/current` | 354 | 17ms | 34ms | 75ms | 78ms |
| `/ranking/me` | 354 | 16ms | 32ms | 43ms | 77ms |

### Write, outbox 빠른 설정, 10 RPS, 60초

outbox 설정:

- `record.outbox.scheduler.fixed-delay-ms=1000`
- `record.outbox.scheduler.batch-size=100`

대상:

- `POST /records`

결과:

| 항목 | 값 |
| --- | ---: |
| 요청 수 | 590 |
| 200 | 590 |
| p50 | 8ms |
| p95 | 13ms |
| p99 | 70ms |
| max | 74ms |

DB 확인:

| 항목 | 값 |
| --- | ---: |
| `running_records` | 590 |
| outbox `DONE` | 590 |
| `weekly_user_stats.run_count` | 590 |

### Write, outbox 빠른 설정, 30 RPS, 60초

outbox 설정:

- `record.outbox.scheduler.fixed-delay-ms=1000`
- `record.outbox.scheduler.batch-size=100`

대상:

- `POST /records`

결과:

| 항목 | 값 |
| --- | ---: |
| 요청 수 | 1770 |
| 200 | 1770 |
| p50 | 13ms |
| p95 | 22ms |
| p99 | 31ms |
| max | 34ms |

DB 확인:

| 항목 | 값 |
| --- | ---: |
| 누적 `running_records` | 2360 |
| outbox `DONE` | 2360 |
| `weekly_user_stats.run_count` | 2360 |

해석:

- 저장 API 자체는 TestDB 기준 30 RPS에서도 안정적이었다.
- outbox 처리 주기를 짧게 하고 batch를 키우면 주간 집계 반영도 따라잡았다.

### Write, 운영 기본 outbox 설정, 10 RPS, 30초

운영 기본 outbox 설정:

- `record.outbox.scheduler.fixed-delay-ms=30000`
- `record.outbox.scheduler.batch-size=20`

대상:

- `POST /records`

결과:

| 항목 | 값 |
| --- | ---: |
| 요청 수 | 290 |
| 200 | 290 |
| p50 | 15ms |
| p95 | 27ms |
| p99 | 188ms |
| max | 196ms |

직후 DB 확인:

| 항목 | 값 |
| --- | ---: |
| `running_records` | 290 |
| outbox `DONE` | 40 |
| outbox `PENDING` | 250 |
| `weekly_user_stats.run_count` | 40 |

해석:

- 저장 API는 성공하지만 운영 기본 outbox 처리량이 기록 저장 속도를 따라가지 못한다.
- 10 RPS 쓰기 30초만으로도 250건 backlog가 남았다.
- 이 상태에서는 주간 집계와 랭킹 반영이 저장 성공보다 늦게 따라온다.

## 장애 로그 분석

2026-06-13 21:11~21:12 KST 알림의 핵심 로그:

```text
BeanCreationException: Error creating bean with name 'flywayInitializer'
FlywaySqlException: Unable to obtain connection from database
FATAL: (EMAXCONNSESSION) max clients reached in session mode
```

직접 원인:

- 애플리케이션 시작 시 Flyway가 DB 연결을 얻지 못했다.
- DB 또는 DB 앞단 pooler의 session-mode client 한도에 도달했다.
- 그 결과 ApplicationContext 생성이 실패했고 컨테이너가 exit code 1로 종료됐다.

현재 코드 기준 관련 설정:

- Hikari 기본 `maximum-pool-size`: 3
- Hikari 기본 `minimum-idle`: 1
- Flyway는 애플리케이션 시작 시 자동 실행
- deploy workflow는 `DB_HIKARI_*` 값을 명시적으로 주입하지 않는다.
- workflow 이름과 Container App 이름은 `backend-api-dev`인데, `SENTRY_ENVIRONMENT`는 `prod`로 설정된다.

확인된 한계:

- 로컬 환경에는 Azure CLI가 없어 Container App의 실제 replica, active revision, scale rule은 확인하지 못했다.
- GitHub CLI 인증 토큰이 만료되어 repository variables의 `SPRING_ENV`도 확인하지 못했다.
- DB/pooler의 실제 session limit도 로컬에서 확인할 권한이 없다.

## 개선 전 기준 결론

1. 공개 랭킹 API는 운영 10 RPS read-only에서 안정적으로 응답했다.
2. 운영 30 RPS read-only에서는 429와 timeout이 발생해 앞단 보호 정책 또는 rate limit 확인이 필요하다.
3. TestDB 기준 저장 API 자체는 30 RPS까지 안정적이었다.
4. 운영 기본 outbox 설정은 10 RPS 쓰기에도 집계 반영이 밀린다.
5. 2026-06-13 장애는 비즈니스 로직 오류가 아니라 DB session-mode client 한도 초과로 인한 startup 실패다.

## 다음 액션

우선 확인:

- Azure Container App의 min/max replica, 현재 replica 수, active revision 수
- DB 또는 pooler의 max client/session 한도
- `api.dalryeo.store`가 dev Container App을 바라보는지, 운영 Container App을 바라보는지
- GitHub Actions `SPRING_ENV`, `DB_HIKARI_*` repository/environment variables

우선 개선:

- outbox 운영값을 `fixed-delay-ms=1000`, `batch-size=100` 이상으로 조정하거나 batch claim 구조로 변경
- Hikari pool size를 DB session 한도와 max replica 수 기준으로 재산정
- Flyway를 모든 app replica startup에서 실행하지 않고 배포 전 migration job으로 분리 검토
- dev/prod 리소스 이름과 `SENTRY_ENVIRONMENT` 값을 정리
- 기록 저장 idempotency key 도입

## Cleanup 확인

TestDB 테스트 후 테스트 계정을 삭제했다.

확인 결과:

| 항목 | 남은 건수 |
| --- | ---: |
| `loadtest_*` 사용자 | 0 |
| 테스트 사용자 기록 | 0 |
| 테스트 사용자 주간 집계 | 0 |

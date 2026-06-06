# 달려 (Dalryeo) Backend

러닝 기록을 관리하고 티어와 주간 랭킹을 제공하는 iOS 앱 **달려**의 백엔드 API 서버입니다.

## 기술 스택

- **Java 17**
- **Spring Boot 3.2.3**
- **Spring Web (MVC)** — `spring-boot-starter-web` 기반의 동기식 REST API 서버
- **Spring Data JPA / Hibernate** — ORM, `ddl-auto: validate`로 스키마 검증
- **PostgreSQL** — 주 데이터베이스
- **Flyway** — DB 스키마 마이그레이션 버전 관리
- **Spring Security + JWT** — 인증·인가 (Apple 로그인 기반)
- **Apple OAuth (Sign in with Apple)** — JWK 기반 토큰 검증
- **springdoc-openapi (Swagger UI)** — API 문서 자동 생성
- **Sentry** — 에러 추적
- **Prometheus + Grafana** — 메트릭 수집 및 시각화
- **Gradle** — 빌드 도구

## 시작하기

### 사전 요구사항

- JDK 17
- PostgreSQL (로컬 또는 접근 가능한 인스턴스)

### 1. 환경변수 설정

실행에 필요한 환경변수를 설정합니다. 필수 항목은 아래와 같습니다. (전체 목록은 [환경변수](#환경변수) 참고)

| 변수 | 설명 |
|---|---|
| `DB_URL` | PostgreSQL 접속 URL (예: `jdbc:postgresql://localhost:5432/dalryeo`) |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 |
| `APP_OAUTH_APPLE_CLIENT_ID` | Apple 로그인 클라이언트 ID |
| `APP_OAUTH_APPLE_ALLOWED_CLIENT_IDS` | 허용할 Apple 클라이언트 ID 목록 |

### 빈 DB 최초 실행

빈 PostgreSQL 인스턴스에서 처음 실행할 때는 먼저 데이터베이스와 접속 계정을 준비합니다. 예시는 로컬 개발용입니다.

```sql
CREATE DATABASE dalryeo;
CREATE USER dalryeo WITH PASSWORD 'dalryeo';
ALTER DATABASE dalryeo OWNER TO dalryeo;
GRANT ALL PRIVILEGES ON DATABASE dalryeo TO dalryeo;
```

그다음 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 위 데이터베이스에 맞게 설정하고 애플리케이션을 실행합니다. Flyway가 `src/main/resources/db/migration`의 마이그레이션을 적용한 뒤 JPA가 `ddl-auto: validate`로 스키마 일치 여부를 검증합니다.

### 2. 실행

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

> 프로파일을 지정하지 않으면 DB 접속 정보 등이 채워지지 않아 실행에 실패할 수 있습니다. 로컬 개발 시에는 `local` 프로파일을 사용하세요.

### 3. 확인

서버가 정상 기동되면 다음에서 확인할 수 있습니다.

- API 서버: `http://localhost:8080`
- API 문서(Swagger): `http://localhost:8080/swagger-ui/index.html`
- 헬스 체크: `http://localhost:8080/actuator/health`

## 환경변수

### 필수

아래 값이 없으면 애플리케이션이 정상 기동되지 않습니다.

| 변수 | 설명 |
|---|---|
| `DB_URL` | PostgreSQL 접속 URL |
| `DB_USERNAME` | DB 사용자명 |
| `DB_PASSWORD` | DB 비밀번호 |
| `JWT_SECRET` | JWT 서명 키 |
| `APP_OAUTH_APPLE_CLIENT_ID` | Apple 로그인 클라이언트 ID |
| `APP_OAUTH_APPLE_ALLOWED_CLIENT_IDS` | 허용할 Apple 클라이언트 ID 목록 |

<details>
<summary><b>선택 변수 (기본값 있음)</b></summary>

지정하지 않으면 괄호 안 기본값이 사용됩니다.

**애플리케이션**
- `APP_PUBLIC_BASE_URL` — 공개 base URL (`https://api.dalryeo.store`)
- `APP_TIME_ZONE` — 서비스 기준 시간대 (`Asia/Seoul`)
- `PROFILE_IMAGE_UPLOAD_DIR` — 프로필 이미지 업로드 경로 (`uploads/profile-images`)

**러닝 기록 Outbox**
- `RECORD_OUTBOX_SCHEDULER_ENABLED` — 스케줄러 활성화 (`true`)
- `RECORD_OUTBOX_SCHEDULER_FIXED_DELAY_MS` — 처리 주기 ms (`5000`)
- `RECORD_OUTBOX_SCHEDULER_BATCH_SIZE` — 배치 크기 (`20`)
- `RECORD_OUTBOX_STALE_TIMEOUT_SECONDS` — 처리 중 이벤트 회수 타임아웃 (`300`)
- `RECORD_OUTBOX_RETRY_MAX_ATTEMPTS` — 최대 재시도 (`10`)
- `RECORD_OUTBOX_RETRY_BASE_DELAY_SECONDS` / `RECORD_OUTBOX_RETRY_MAX_DELAY_SECONDS` — 재시도 backoff (`30` / `1800`)

**주간 티어 마감**
- `WEEKLY_TIER_FINALIZATION_ENABLED` — 활성화 (`true`)
- `WEEKLY_TIER_FINALIZATION_CRON` — 실행 cron (`0 10 0 * * MON`)
- `WEEKLY_TIER_FINALIZATION_LOOKBACK_WEEKS` — 소급 집계 주 수 (`4`)

**모니터링**
- `SENTRY_DSN` — Sentry 연동 키
- `SENTRY_ENVIRONMENT` — 환경 태그 (`prod`)
- `SENTRY_RELEASE` — 릴리스 버전 (`local`)

</details>

## 프로젝트 구조

기능(도메인)별로 패키지를 나눈 레이어드 아키텍처입니다. 각 모듈은 필요한 책임에 따라 `controller`, `service`, `repository`, `entity`, `dto` 계층을 둡니다.

```
com.ohgiraffers.dalryeo
├── auth         # 인증/인가 — Apple 로그인, JWT 발급·검증
├── onboarding   # 신규 사용자 온보딩
├── user         # 사용자 도메인
├── record       # 러닝 기록 — 저장 및 Outbox 기반 비동기 이벤트 처리
├── analysis     # 러닝 기록 분석/통계
├── ranking      # 랭킹 조회
├── tier         # 티어 도메인
├── weeklytier   # 주간 티어 마감 — 스케줄러 기반 주간 집계
├── mypage       # 마이페이지 프로필 수정
├── common       # 공통 (예외 처리, 응답 포맷 등)
└── config       # 전역 설정 (WebMvc, Clock, Sentry, 정적 리소스, 설정 프로퍼티 등)
```

## API 문서

전체 API 명세는 Swagger UI에서 확인할 수 있습니다.

- 로컬 실행 후: `http://localhost:8080/swagger-ui/index.html`

Swagger는 기본값과 운영(prod) 환경에서 닫혀 있고, `local` 프로파일에서만 열립니다. 운영에서는 보안을 위해 Swagger UI와 API 문서(`/v3/api-docs`)가 모두 비활성화되어 있으며, 해당 경로는 404 응답을 반환해야 합니다.

## 아키텍처 메모

### 러닝 기록 저장과 Outbox 패턴

러닝 기록 저장은 **Transactional Outbox 패턴**으로 처리됩니다. 기록을 저장하는 트랜잭션에서 후속 작업(랭킹·집계 반영 등)을 직접 호출하지 않고, 같은 트랜잭션 안에서 이벤트를 outbox 테이블에 함께 기록합니다. 이후 별도 스케줄러가 이 이벤트를 비동기로 처리합니다.

이렇게 분리한 이유는, 기록 저장과 후속 처리를 한 트랜잭션에 묶으면 후속 처리가 실패할 때 기록 저장까지 롤백되거나 응답이 느려지기 때문입니다. Outbox는 "기록은 확실히 저장하고, 후속 처리는 안전하게 재시도한다"를 보장합니다. 처리 중 중단된 이벤트는 타임아웃으로 회수되고, 실패한 이벤트는 backoff 재시도 후 실패 처리됩니다. (관련 설정: `RECORD_OUTBOX_*`)

### 주간 티어 마감 스케줄러

주간 티어는 매주 정해진 시각(기본: 월요일 00:10, `Asia/Seoul`)에 스케줄러가 자동 집계·마감합니다. 누락된 주를 보완하기 위해 최근 몇 주를 소급 집계합니다. (관련 설정: `WEEKLY_TIER_*`)

## 주의사항

- **DB 스키마 검증 모드**: `ddl-auto`가 `validate`로 설정되어 있어, 엔티티와 실제 DB 스키마가 일치하지 않으면 애플리케이션이 기동되지 않습니다. 스키마 변경은 Flyway 마이그레이션으로 관리합니다.
- **시간대**: 서비스 기준 시간대는 `Asia/Seoul`이며, 주간 티어 집계 등 시간 의존 로직이 이를 기준으로 동작합니다. 날짜·시간 데이터는 offset(`OffsetDateTime`)을 포함해 다룹니다.
- **Swagger 노출**: API 문서는 `local` 프로파일에서만 활성화됩니다. 기본값과 `prod` 프로파일에서는 `springdoc.api-docs.enabled=false`, `springdoc.swagger-ui.enabled=false`로 비활성화됩니다.
- **에러 응답 정책**: 운영 환경에서는 스택트레이스·내부 메시지 등 상세 정보를 응답에 노출하지 않습니다.

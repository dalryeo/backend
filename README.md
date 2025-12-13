# 달려 (Dalryeo) - 러닝 기록 관리 백엔드 API

달려는 러닝 기록을 관리하고 랭킹을 제공하는 백엔드 서비스입니다.

## 📋 목차

- [기술 스택](#기술-스택)
- [프로젝트 구조](#프로젝트-구조)
- [주요 기능](#주요-기능)
- [API 엔드포인트](#api-엔드포인트)
- [설정 방법](#설정-방법)
- [실행 방법](#실행-방법)
- [데이터베이스 스키마](#데이터베이스-스키마)

## 🛠 기술 스택

- **Java**: 17
- **Spring Boot**: 4.0.0
- **Spring Data JPA**: 데이터베이스 접근
- **MySQL**: 데이터베이스
- **JWT**: 인증 토큰 관리
- **Lombok**: 보일러플레이트 코드 감소
- **Gradle**: 빌드 도구

### 주요 의존성

- `spring-boot-starter-data-jpa`: JPA 및 데이터베이스 지원
- `spring-boot-starter-webmvc`: REST API 지원
- `spring-boot-starter-validation`: 요청 데이터 검증
- `io.jsonwebtoken:jjwt`: JWT 토큰 생성/검증
- `com.nimbusds:nimbus-jose-jwt`: Apple OAuth 토큰 검증

## 📁 프로젝트 구조

```
src/main/java/com/ohgiraffers/dalryeo/
├── auth/                    # 인증 관련
│   ├── controller/         # AuthController
│   ├── dto/                # 요청/응답 DTO
│   ├── entity/             # User 엔티티
│   ├── exception/          # 예외 처리
│   ├── jwt/                # JWT 토큰 관리
│   ├── oauth/              # Apple OAuth 검증
│   ├── repository/         # UserRepository
│   └── service/            # AuthService
├── onboarding/             # 온보딩 관련
│   ├── controller/         # OnboardingController
│   ├── dto/                # 요청/응답 DTO
│   └── service/            # OnboardingService
├── record/                 # 러닝 기록 관련
│   ├── controller/         # RecordController
│   ├── dto/                # 요청/응답 DTO
│   ├── entity/             # RunningRecord 엔티티
│   ├── repository/        # RunningRecordRepository
│   └── service/            # RecordService
├── analysis/               # 분석 관련
│   ├── controller/         # AnalysisController
│   ├── dto/                # 요청/응답 DTO
│   └── service/            # AnalysisService
├── ranking/                # 랭킹 관련
│   ├── controller/         # RankingController
│   ├── dto/                # 요청/응답 DTO
│   └── service/            # RankingService
└── common/                 # 공통 클래스
    └── CommonResponse.java # 공통 응답 형식
```

## ✨ 주요 기능

### 1. 인증 (Auth)
- Apple OAuth 로그인
- JWT 토큰 기반 인증
- Refresh Token 재발급
- 로그아웃 및 회원 탈퇴

### 2. 온보딩 (Onboarding)
- 닉네임 중복 체크
- 온보딩 정보 저장 (닉네임, 성별, 생년월일, 키, 몸무게, 프로필 이미지)
- 예상 티어 계산

### 3. 기록 (Records)
- 러닝 기록 저장
- 주간 요약 정보 조회
- 주간 기록 목록 조회

### 4. 분석 (Analysis)
- 전체 기록 조회 (페이징, 정렬, 기간 필터)
- 기록 상세 조회

### 5. 랭킹 (Ranking)
- 점수 기반 주간 랭킹
- 거리 기반 주간 랭킹

## 🔌 API 엔드포인트

### 인증 (Auth)

#### 1. Apple OAuth 로그인
```http
POST /auth/oauth/apple
Content-Type: application/json

{
  "identityToken": "xxx.yyy.zzz"
}
```

**응답:**
```json
{
  "success": true,
  "data": {
    "accessToken": "jwt-access",
    "refreshToken": "jwt-refresh",
    "isNewUser": true
  }
}
```

#### 2. Refresh Token 재발급
```http
POST /auth/token/refresh
Content-Type: application/json

{
  "refreshToken": "string"
}
```

#### 3. 로그아웃
```http
POST /auth/logout
Authorization: Bearer {accessToken}
```

#### 4. 회원 탈퇴
```http
DELETE /auth/withdraw
Authorization: Bearer {accessToken}
```

### 온보딩 (Onboarding)

#### 1. 닉네임 중복 체크
```http
GET /onboarding/nickname/check?nickname=abc
```

#### 2. 온보딩 정보 저장
```http
POST /onboarding
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "nickname": "서현",
  "gender": "F",
  "birth": "2020-01-01",
  "height": 177,
  "weight": 77,
  "profileImage": null
}
```

#### 3. 예상 티어 계산
```http
POST /onboarding/estimate-tier
Content-Type: application/json

{
  "distanceKm": 3.2,
  "paceSecPerKm": 350
}
```

### 기록 (Records)

#### 1. 러닝 기록 저장
```http
POST /records
Authorization: Bearer {accessToken}
Content-Type: application/json

{
  "platform": "IOS",
  "distanceKm": 5.01,
  "durationSec": 1600,
  "avgPaceSecPerKm": 320,
  "avgHeartRate": 148,
  "caloriesKcal": 340,
  "startAt": "2025-11-12T07:00:00",
  "endAt": "2025-11-12T07:26:40"
}
```

#### 2. 기록 탭 메인 정보 (주간 요약)
```http
GET /records/summary
Authorization: Bearer {accessToken}
```

#### 3. 주간 기록 목록
```http
GET /records/weekly
Authorization: Bearer {accessToken}
```

### 분석 (Analysis)

#### 1. 전체 기록 조회
```http
GET /analysis/records?page=1&sort=latest&period=monthly
Authorization: Bearer {accessToken}
```

**쿼리 파라미터:**
- `page`: 페이지 번호 (기본값: 1)
- `sort`: 정렬 옵션 (`latest`, `oldest`, `distance`)
- `period`: 기간 필터 (`monthly` 또는 미지정 시 전체)

#### 2. 기록 상세 조회
```http
GET /analysis/records/{recordId}
Authorization: Bearer {accessToken}
```

### 랭킹 (Ranking)

#### 1. 점수 기반 주간 랭킹
```http
GET /ranking/weekly/score
```

#### 2. 거리 기반 주간 랭킹
```http
GET /ranking/weekly/distance
```

## ⚙️ 설정 방법

### 1. application.properties 설정

`src/main/resources/application.properties` 파일에 다음 설정을 추가하세요:

```properties
spring.application.name=dalryeo

# 데이터베이스 설정
spring.datasource.url=jdbc:mysql://localhost:3306/your_database
spring.datasource.username=your_username
spring.datasource.password=your_password
spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver

# JPA 설정
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.format_sql=true

# JWT 설정
jwt.secret=your-secret-key-change-this-in-production-use-long-random-string
jwt.access-token-expiration=3600000
jwt.refresh-token-expiration=604800000
```

### 2. JWT Secret 변경

프로덕션 환경에서는 반드시 `jwt.secret` 값을 강력한 랜덤 문자열로 변경하세요.

## 🚀 실행 방법

### 1. 데이터베이스 생성

MySQL에서 데이터베이스를 생성합니다:

```sql
CREATE DATABASE dalryeo CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. Gradle 빌드

```bash
./gradlew build
```

### 3. 애플리케이션 실행

```bash
./gradlew bootRun
```

또는 IDE에서 `DalryeoApplication.java`를 실행합니다.

### 4. API 테스트

애플리케이션이 실행되면 기본적으로 `http://localhost:8080`에서 접근할 수 있습니다.

## 📊 데이터베이스 스키마

### users 테이블
- `id`: 사용자 ID (PK)
- `apple_id`: Apple ID (UNIQUE)
- `refresh_token`: Refresh Token
- `is_withdrawn`: 탈퇴 여부
- `nickname`: 닉네임 (UNIQUE)
- `gender`: 성별 (F/M)
- `birth`: 생년월일
- `height`: 키
- `weight`: 몸무게
- `profile_image`: 프로필 이미지 URL
- `current_tier`: 현재 티어
- `current_tier_grade`: 현재 티어 등급
- `tier_score`: 티어 점수
- `created_at`: 생성일시
- `updated_at`: 수정일시

### running_records 테이블
- `id`: 기록 ID (PK)
- `user_id`: 사용자 ID (FK)
- `platform`: 플랫폼 (IOS/ANDROID)
- `distance_km`: 거리 (km)
- `duration_sec`: 시간 (초)
- `avg_pace_sec_per_km`: 평균 페이스 (초/km)
- `avg_heart_rate`: 평균 심박수
- `calories_kcal`: 소모 칼로리
- `start_at`: 시작 시간
- `end_at`: 종료 시간
- `created_at`: 생성일시
- `updated_at`: 수정일시

## ⚠️ 주의사항

1. **Apple OAuth 검증**: 현재 `AppleOAuthValidator`는 기본적인 토큰 파싱만 수행합니다. 프로덕션 환경에서는 Apple의 공개키를 사용하여 실제 서명 검증을 구현해야 합니다.

2. **JWT Secret**: 프로덕션 환경에서는 반드시 강력한 랜덤 문자열로 변경하세요.

3. **데이터베이스**: `spring.jpa.hibernate.ddl-auto=update`는 개발 환경용입니다. 프로덕션에서는 `validate` 또는 `none`을 사용하세요.

4. **티어 계산 로직**: `OnboardingService`의 `calculateTier()` 메서드는 예시 구현입니다. 실제 비즈니스 로직에 맞게 수정이 필요합니다.

## 📝 에러 코드

| 코드 | 설명 |
|------|------|
| AC-003 | OAuth 토큰 검증 실패 |
| AC-004 | refreshToken 불일치 |
| AC-006 | refreshToken 만료 또는 탈퇴된 사용자 |

## 🔐 인증

대부분의 API는 JWT Access Token이 필요합니다. 요청 헤더에 다음과 같이 포함하세요:

```
Authorization: Bearer {accessToken}
```

## 📄 라이선스

이 프로젝트는 내부 사용을 위한 프로젝트입니다.

# Onboarding Domain

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-23

## 도메인 개요

Onboarding 도메인은 새 사용자가 달려에서 활동할 수 있도록 기본 프로필 정보를 완성하고, 초기 화면에 필요한 표시 정보를 제공하는 도메인이다. 온보딩 정보는 사용자 식별 이후에 채워지는 프로필 상태이며, 닉네임, 성별, 생년월일, 키, 몸무게, 프로필 이미지를 포함한다.

Onboarding은 사용자의 초기 표시 상태를 다루지만, 인증 수단을 검증하거나 실제 티어 상태를 확정하지 않는다. 예상 티어는 미리보기이며, 현재 표시 티어는 Tier 도메인의 스냅샷 정책을 따른다.

## 핵심 책임

- 닉네임 사용 가능 여부를 확인한다.
- 온보딩 필수 프로필 정보를 저장한다.
- 온보딩 정보를 조회한다.
- 프로필 이미지를 업로드하고 사용자 프로필 이미지로 연결한다.
- 사용자의 입력 거리와 페이스로 예상 티어를 계산한다.
- 기본 프로필 이미지와 커스텀 프로필 이미지의 표시 우선순위를 결정한다.

## 도메인 경계

Onboarding 도메인이 책임지는 것:

- 온보딩 필수 입력값을 사용자 프로필에 반영한다.
- 닉네임 중복 여부를 확인하고, 현재 사용자 닉네임과의 충돌은 허용한다.
- 커스텀 프로필 이미지를 저장하고 이전 관리 이미지 정리를 요청한다.
- 온보딩 조회에서 표시 프로필 이미지와 티어 표시값을 조합한다.
- 예상 티어 응답을 만든다.

Onboarding 도메인이 책임지지 않는 것:

- Apple OAuth identity token을 검증하지 않는다.
- Access Token 또는 Refresh Token을 발급하지 않는다.
- 실제 주간 티어 스냅샷을 생성하거나 갱신하지 않는다.
- 러닝 기록을 생성하지 않는다.
- 주간 집계를 생성하거나 갱신하지 않는다.
- 현재 `PUT /onboarding`의 프로필 수정 로직은 Mypage service에 위임되어 있다.

## 주요 모델

### 온보딩 프로필

_User Profile State_

온보딩 프로필은 `User`에 저장되는 초기 사용자 정보다.

#### 속성

- `nickname`: 서비스 내 표시 이름
- `gender`: `F`, `M`, `O` 중 하나
- `birth`: 생년월일
- `height`: 키
- `weight`: 몸무게
- `profileImage`: 커스텀 프로필 이미지 경로

#### 규칙

- 온보딩 저장은 인증된 사용자 기준으로 수행한다.
- 닉네임은 30자 이하여야 한다.
- 닉네임은 다른 사용자가 이미 사용 중이면 저장할 수 없다.
- 성별은 `F`, `M`, `O` 중 하나여야 한다.
- 생년월일은 `yyyy-MM-dd` 형식의 날짜다.
- 키와 몸무게는 필수값이다.
- 빈 프로필 이미지 값은 `null`로 정규화한다.

### 닉네임 사용 가능 여부

_Query Result_

닉네임 확인은 사용자가 입력한 닉네임을 현재 사용할 수 있는지 알려주는 조회다.

#### 속성

- `available`: 사용 가능 여부

#### 규칙

- 닉네임 확인 요청은 30자 이하여야 한다.
- 닉네임 존재 여부는 `users.nickname` 기준으로 확인한다.
- 저장 또는 수정 시점에는 다시 중복 검증을 수행해야 한다.
- 닉네임 확인 결과만으로 닉네임 점유가 보장되지는 않는다.

### 프로필 이미지

_Stored Asset Reference_

프로필 이미지는 사용자의 커스텀 이미지 경로다.

#### 속성

- `imageUrl`: 저장된 이미지 접근 경로
- `uploadDir`: 서버 파일 저장 위치
- `urlPrefix`: 사용자 프로필 이미지 URL prefix

#### 규칙

- 업로드 파일은 비어 있을 수 없다.
- content type이 있으면 `image/`로 시작해야 한다.
- 허용 확장자는 `jpg`, `jpeg`, `png`, `gif`, `webp`다.
- 저장 파일명은 사용자 ID와 UUID를 포함해 충돌을 피한다.
- 저장 경로는 설정된 업로드 디렉터리 밖으로 벗어나면 안 된다.
- 사용자의 프로필 이미지가 바뀌면 이전 관리 이미지는 best-effort로 삭제한다.
- 티어 기본 프로필 이미지는 커스텀 프로필 이미지 저장 정책의 대상이 아니다.

### 온보딩 조회 결과

_Read Model_

온보딩 조회 결과는 사용자 프로필과 현재 표시 티어 정보를 조합한다.

#### 속성

- `nickname`
- `gender`
- `birth`
- `height`
- `weight`
- `displayProfileImage`
- `customProfileImage`
- `tierCode`
- `tierGrade`
- `defaultProfileImage`

#### 규칙

- 커스텀 프로필 이미지가 있으면 `displayProfileImage`는 커스텀 이미지를 우선 사용한다.
- 커스텀 프로필 이미지가 없으면 현재 티어의 기본 프로필 이미지를 사용한다.
- 확정된 현재 티어 스냅샷이 있으면 그 값을 표시한다.
- 확정된 현재 티어 스냅샷이 없고 온보딩이 완료된 사용자라면 `TURTLE / B` 기본 표시값을 사용할 수 있다.
- 온보딩이 완료되지 않은 사용자는 티어 표시값과 기본 프로필 이미지가 비어 있을 수 있다.
- 티어 기본 이미지 경로가 `/profiles/tiers/`로 시작하면 public base URL을 붙여 절대 URL로 응답한다.

### 예상 티어

_Preview Result_

예상 티어는 사용자가 입력한 거리와 페이스로 계산한 미리보기다.

#### 속성

- `distanceKm`: 예상 기록 거리
- `paceSecPerKm`: 예상 평균 페이스
- `tierCode`: 예상 티어 코드
- `displayName`: 예상 티어 표시 이름
- `tierGrade`: 예상 티어 등급
- `score`: 예상 기록 점수

#### 규칙

- 예상 티어 계산은 인증된 사용자만 요청할 수 있다.
- 거리와 페이스는 양수여야 한다.
- 예상 티어는 실제 러닝 기록을 만들지 않는다.
- 예상 티어는 `weekly_user_stats`를 만들거나 갱신하지 않는다.
- 예상 티어는 `weekly_tiers` 스냅샷을 만들거나 갱신하지 않는다.
- 예상 티어는 Tier 도메인의 기록 점수 산식과 티어 메타데이터를 사용한다.

## 도메인 규칙

- 온보딩 완료 여부는 닉네임, 성별, 생년월일, 키, 몸무게가 모두 채워졌는지로 판단한다.
- 온보딩은 사용자 상태의 원본 저장소인 `User`를 갱신하지만, 인증 수단 자체를 소유하지 않는다.
- 닉네임 중복 확인과 저장 시점의 닉네임 검증은 분리되어야 한다.
- 커스텀 프로필 이미지가 있으면 티어 기본 이미지는 표시 우선순위에서 밀린다.
- 예상 티어는 실제 티어 상태가 아니다.
- 현재 표시 티어는 Tier/WeeklyTier의 확정 스냅샷 정책을 따른다.

## 타 도메인과의 관계

| 도메인 | 관계 |
| --- | --- |
| Auth/User | 인증된 사용자와 `User` 프로필 상태를 제공한다. Onboarding은 이 상태를 채운다. |
| Tier | 예상 티어 계산과 현재 표시 티어, 기본 프로필 이미지를 제공한다. |
| Mypage | 현재 `PUT /onboarding` 프로필 수정 요청은 Mypage service가 처리한다. |
| Record | 예상 티어가 실제 기록을 만들지 않는다는 경계를 공유한다. |
| WeeklyTier | 온보딩 조회의 현재 표시 티어는 확정 스냅샷을 기준으로 한다. |

## 구현 앵커

### API

- `GET /onboarding/nickname/check`
- `POST /onboarding`
- `PUT /onboarding`
- `POST /onboarding/profile-image`
- `GET /onboarding`
- `POST /onboarding/estimate-tier`

### 코드

- [OnboardingController](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/controller/OnboardingController.java)
- [OnboardingService](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/service/OnboardingService.java)
- [ProfileImageStorageService](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/service/ProfileImageStorageService.java)
- [OnboardingRequest](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/dto/OnboardingRequest.java)
- [OnboardingResponse](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/dto/OnboardingResponse.java)
- [EstimateTierRequest](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/dto/EstimateTierRequest.java)
- [EstimateTierResponse](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/dto/EstimateTierResponse.java)
- [ProfileImageStorageProperties](../../src/main/java/com/ohgiraffers/dalryeo/config/ProfileImageStorageProperties.java)
- [MypageService](../../src/main/java/com/ohgiraffers/dalryeo/mypage/service/MypageService.java)

### 대표 테스트

- [OnboardingServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/onboarding/service/OnboardingServiceTest.java)
- [ProfileImageStorageServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/onboarding/service/ProfileImageStorageServiceTest.java)
- [MypageServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/mypage/service/MypageServiceTest.java)
- [ApiContractIntegrationTest](../../src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java)

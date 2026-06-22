# Mypage Domain

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-16

## 도메인 개요

Mypage 도메인은 온보딩 이후 사용자가 자신의 프로필 정보를 수정하는 도메인이다. 현재 구현 기준에서 Mypage는 독립된 controller 경로를 갖지 않고, `PUT /onboarding` 요청을 통해 호출된다.

Mypage의 중심 책임은 이미 존재하는 활성 사용자의 프로필 상태를 새 입력값으로 교체하는 것이다. 초기 온보딩 저장, 프로필 이미지 파일 업로드, 현재 티어 표시, 인증 토큰 처리는 Mypage의 책임이 아니다.

## 핵심 책임

- 활성 사용자의 프로필 수정 요청을 처리한다.
- 닉네임 변경 시 중복 여부를 검증한다.
- 닉네임, 성별, 생년월일, 키, 몸무게, 프로필 이미지 경로를 갱신한다.
- 빈 프로필 이미지 값을 `null`로 정규화한다.
- 프로필 이미지가 바뀌면 이전 관리 이미지 정리를 요청한다.

## 도메인 경계

Mypage 도메인이 책임지는 것:

- 현재 로그인 사용자의 프로필 수정 가능 여부를 활성 사용자 기준으로 확인한다.
- 수정 요청의 프로필 값을 `User`에 반영한다.
- 새 닉네임이 현재 닉네임과 다르고 이미 사용 중이면 수정하지 않는다.
- 프로필 이미지 참조가 바뀌면 이전 이미지를 정리한다.

Mypage 도메인이 책임지지 않는 것:

- 신규 사용자의 초기 온보딩 저장을 소유하지 않는다.
- 닉네임 사용 가능 여부 조회 API를 소유하지 않는다.
- 프로필 이미지 파일 업로드와 저장 파일명 생성을 소유하지 않는다.
- 티어 기본 프로필 이미지를 결정하지 않는다.
- 현재 표시 티어를 조회하거나 계산하지 않는다.
- 인증 토큰을 검증하거나 발급하지 않는다.

## 주요 모델

### 프로필 수정 요청

_Input Contract_

`ProfileUpdateRequest`는 사용자가 프로필 수정 시 제출하는 입력값이다.

#### 속성

- `nickname`: 서비스 내 표시 이름
- `gender`: `F`, `M`, `O` 중 하나
- `birth`: 생년월일
- `height`: 키
- `weight`: 몸무게
- `profileImage`: 커스텀 프로필 이미지 경로

#### 규칙

- 닉네임은 필수값이다.
- 성별은 필수값이며 `F`, `M`, `O` 중 하나여야 한다.
- 생년월일은 필수값이며 `yyyy-MM-dd` 형식의 날짜다.
- 키와 몸무게는 필수값이다.
- 프로필 이미지는 선택값이다.
- `profileImage`가 `null`이거나 blank면 사용자 커스텀 프로필 이미지를 비우는 요청으로 본다.

### 수정 대상 사용자

_Entity State_

Mypage는 `User`의 프로필 필드를 수정한다.

#### 속성

- `nickname`
- `gender`
- `birth`
- `height`
- `weight`
- `profileImage`

#### 행위

- `updateProfile(...)`: 프로필 수정 요청값을 사용자 상태에 반영한다.

#### 규칙

- 수정 대상 사용자는 활성 사용자여야 한다.
- 탈퇴 사용자는 프로필을 수정할 수 없다.
- 새 닉네임이 현재 닉네임과 같으면 중복으로 보지 않는다.
- 새 닉네임이 현재 닉네임과 다르고 이미 존재하면 수정할 수 없다.
- 프로필 수정은 요청에 포함된 프로필 필드를 한 번에 교체한다.

### 프로필 이미지 참조

_Stored Asset Reference_

Mypage는 이미 저장되어 전달된 프로필 이미지 경로를 사용자 프로필에 연결하거나 제거한다.

#### 속성

- `previousProfileImage`: 수정 전 사용자 프로필 이미지
- `newProfileImage`: 정규화된 수정 요청의 프로필 이미지

#### 규칙

- 새 프로필 이미지 값이 비어 있으면 `null`로 저장한다.
- 이전 이미지와 새 이미지가 다르면 이전 이미지 정리를 요청한다.
- 이전 이미지 삭제는 `ProfileImageStorageService`의 관리 경로 판단과 best-effort 삭제 정책을 따른다.
- Mypage는 업로드 파일의 content type, 확장자, 저장 경로를 검증하지 않는다.

## 도메인 규칙

- Mypage는 온보딩 이후 프로필 수정 책임을 가진다.
- 현재 API 노출 경로가 `PUT /onboarding`이어도 수정 로직의 소유자는 Mypage service다.
- 프로필 수정은 인증된 활성 사용자에게만 허용된다.
- 닉네임 중복 검증은 저장 직전에 다시 수행되어야 한다.
- 커스텀 프로필 이미지를 제거하면 조회 화면에서는 다른 도메인의 표시 정책에 따라 티어 기본 이미지가 사용될 수 있다.
- Mypage는 티어 기본 이미지나 현재 표시 티어를 직접 계산하지 않는다.

## 타 도메인과의 관계

| 도메인 | 관계 |
| --- | --- |
| Auth/User | 인증된 사용자와 `User` 프로필 상태를 제공한다. Mypage는 활성 사용자만 수정한다. |
| Onboarding | 현재 `PUT /onboarding` endpoint가 Mypage service로 프로필 수정을 위임한다. 초기 온보딩 저장과 조회는 Onboarding 책임이다. |
| Profile Image | Mypage는 저장된 이미지 경로를 프로필에 연결하거나 제거한다. 파일 업로드와 저장 검증은 ProfileImageStorageService 책임이다. |
| Tier | 커스텀 이미지가 없을 때 표시될 기본 티어 이미지는 Tier/Onboarding 표시 정책을 따른다. |

## 구현 앵커

### API

- `PUT /onboarding`

### 코드

- [MypageService](../../src/main/java/com/ohgiraffers/dalryeo/mypage/service/MypageService.java)
- [ProfileUpdateRequest](../../src/main/java/com/ohgiraffers/dalryeo/mypage/dto/ProfileUpdateRequest.java)
- [OnboardingController](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/controller/OnboardingController.java)
- [User](../../src/main/java/com/ohgiraffers/dalryeo/auth/entity/User.java)
- [UserLookupService](../../src/main/java/com/ohgiraffers/dalryeo/user/service/UserLookupService.java)
- [ProfileImageStorageService](../../src/main/java/com/ohgiraffers/dalryeo/onboarding/service/ProfileImageStorageService.java)

### 대표 테스트

- [MypageServiceTest](../../src/test/java/com/ohgiraffers/dalryeo/mypage/service/MypageServiceTest.java)
- [ApiContractIntegrationTest](../../src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java)

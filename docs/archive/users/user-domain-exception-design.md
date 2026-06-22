# 사용자 도메인 예외 체계 정리 필요성

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

관련 이슈: #18

## 배경

주간 집계 기반 랭킹 조회 구조를 개선하는 과정에서
`RankingService`의 사용자 조회 실패 처리가 리뷰에서 지적되었습니다.

기존 코드는 아래처럼 일반 `RuntimeException`을 사용했습니다.

```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new RuntimeException("사용자를 찾을 수 없습니다."));
```

이 방식은 동작은 하지만 백엔드 API 관점에서는 부족합니다.
예외 타입만 보고 어떤 종류의 실패인지 알기 어렵고,
클라이언트가 에러 코드 기준으로 분기하기도 어렵습니다.

## 현재 프로젝트의 예외 처리 상태

프로젝트에는 이미 일부 도메인 예외 구조가 있습니다.

- `AuthException` + `AuthErrorCode`
- `RecordValidationException` + `RecordValidationErrorCode`
- `GlobalExceptionHandler`

즉 완전히 새로운 방향을 도입해야 하는 것이 아니라,
이미 있는 패턴을 사용자 도메인에도 확장하는 것이 자연스럽습니다.

현재는 사용자 관련 오류가 여러 서비스에 흩어져 있습니다.

- 사용자 조회 실패
- 탈퇴 사용자 접근
- 중복 닉네임
- OAuth 사용자 매핑 오류

이 중 일부는 `RuntimeException`,
일부는 `IllegalArgumentException`,
일부는 `AuthException`으로 처리될 수 있어
API 실패 응답 기준이 일관되지 않을 수 있습니다.

## 왜 RuntimeException이 부족한가

`RuntimeException`은 너무 넓은 예외입니다.

```text
사용자를 찾을 수 없음
탈퇴한 사용자
중복 닉네임
잘못된 상태 전이
서버 내부 오류
```

위 상황들이 모두 `RuntimeException`으로 표현되면,
서버 로그나 API 응답만 보고 정확한 실패 원인을 구분하기 어렵습니다.

클라이언트 입장에서도 문제가 있습니다.

```text
USER_NOT_FOUND면 로그인 화면으로 보낼지
WITHDRAWN_USER면 재가입 안내를 보여줄지
DUPLICATED_NICKNAME이면 입력 폼에 에러를 표시할지
```

이런 분기를 하려면 메시지 문자열보다 안정적인 에러 코드가 필요합니다.

## 이전 PR에서 분리한 이유

이 문제는 맞는 지적이지만,
주간 집계 테이블과 랭킹 조회 성능 개선 PR에 함께 넣기에는 범위가 커집니다.

이번 PR의 핵심은 아래입니다.

- `weekly_user_stats` 집계 테이블 추가
- 기록 저장과 집계 갱신 트랜잭션 처리
- 랭킹 조회를 집계 테이블 기준으로 전환
- 집계 upsert와 인덱스 설계

사용자 도메인 예외 체계 정리는 API 실패 응답 계약을 바꾸는 작업입니다.
따라서 별도 이슈로 분리해서 진행하는 것이 좋습니다.

## 공통 파싱 예외와 구분

사용자 도메인 예외는 `HttpMessageNotReadableException` 같은 Spring 공통 예외와 구분해야 합니다.

`HttpMessageNotReadableException`은 요청 JSON을 DTO로 변환하는 과정에서
Spring/Jackson이 던지는 예외입니다.

예를 들면 아래 상황입니다.

- `OffsetDateTime` 필드에 timezone offset 없는 문자열이 들어온 경우
- 숫자 필드에 문자열이 들어온 경우
- JSON 문법 자체가 깨진 경우

이 예외는 컨트롤러나 서비스 로직에 도달하기 전에 발생하므로,
`UserException`이나 `RecordValidationException` 같은 도메인 커스텀 예외와 성격이 다릅니다.

따라서 작업을 아래처럼 분리합니다.

- 사용자 도메인 예외: 사용자를 찾을 수 없음, 탈퇴 사용자, 중복 닉네임 등 비즈니스 규칙 실패를 표현합니다.
- JSON 파싱 실패 예외: 잘못된 요청 본문과 타입 변환 실패를 전역 공통 응답으로 가공합니다.

관련 문서:

```text
docs/archive/common-exception/json-parse-error-response-improvement.md
```

## 개선 방향

사용자 도메인 예외를 별도로 정의합니다.

```text
UserErrorCode
UserException
```

예상 에러 코드는 아래처럼 둘 수 있습니다.

```text
USER-001 USER_NOT_FOUND                 사용자를 찾을 수 없습니다.              404 Not Found
USER-002 WITHDRAWN_USER                 탈퇴한 사용자입니다.                  403 Forbidden
USER-003 DUPLICATED_NICKNAME            이미 사용 중인 닉네임입니다.          409 Conflict
USER-004 OAUTH_USER_MAPPING_INVALID     OAuth 사용자 매핑이 올바르지 않습니다. 500 Internal Server Error
```

서비스 코드에서는 아래처럼 사용합니다.

```java
User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
```

`GlobalExceptionHandler`에서는 `UserException`을 받아
공통 실패 응답으로 변환합니다.

```json
{
  "success": false,
  "data": {
    "code": "USER-001",
    "message": "사용자를 찾을 수 없습니다."
  }
}
```

## 구현 내용

### 1. UserErrorCode와 UserException 추가

사용자 도메인에서 클라이언트가 구분해야 하는 실패를
문자열 메시지가 아니라 안정적인 코드로 표현합니다.

```java
public enum UserErrorCode {
    USER_NOT_FOUND("USER-001", "사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    WITHDRAWN_USER("USER-002", "탈퇴한 사용자입니다.", HttpStatus.FORBIDDEN),
    DUPLICATED_NICKNAME("USER-003", "이미 사용 중인 닉네임입니다.", HttpStatus.CONFLICT),
    OAUTH_USER_MAPPING_INVALID("USER-004", "OAuth 사용자 매핑이 올바르지 않습니다.", HttpStatus.INTERNAL_SERVER_ERROR);
}
```

각 에러 코드에 HTTP 상태를 같이 둔 이유는
같은 `UserException`이어도 실패 성격이 다르기 때문입니다.

```text
없는 사용자: 404
탈퇴 사용자: 403
중복 닉네임: 409
OAuth 매핑 불일치: 500
```

### 2. UserLookupService 추가

여러 서비스에 반복되던 사용자 조회 로직을 한 곳으로 모았습니다.

```java
public User getById(Long userId) {
    return userRepository.findById(userId)
            .orElseThrow(() -> new UserException(UserErrorCode.USER_NOT_FOUND));
}

public User getActiveById(Long userId) {
    User user = getById(userId);
    if (user.isWithdrawn()) {
        throw new UserException(UserErrorCode.WITHDRAWN_USER);
    }
    return user;
}
```

닉네임 중복 검증도 사용자 도메인 규칙이므로 같은 서비스로 모았습니다.

```java
public void validateNicknameAvailable(String newNickname, String currentNickname) {
    if (newNickname != null && !newNickname.equals(currentNickname) && userRepository.existsByNickname(newNickname)) {
        throw new UserException(UserErrorCode.DUPLICATED_NICKNAME);
    }
}
```

이렇게 분리하면 각 서비스가 아래 판단을 직접 반복하지 않아도 됩니다.

```text
사용자가 존재하는가?
탈퇴한 사용자 접근인가?
닉네임을 변경할 수 있는가?
실패 시 어떤 에러 코드를 내려야 하는가?
```

### 3. GlobalExceptionHandler 연결

`UserException`은 `GlobalExceptionHandler`에서 공통 실패 응답으로 변환합니다.

```java
@ExceptionHandler(UserException.class)
public ResponseEntity<CommonResponse<Map<String, Object>>> handleUserException(UserException e) {
    return ResponseEntity
            .status(e.getErrorCode().getStatus())
            .body(CommonResponse.failure(errorBody(
                    e.getErrorCode().getCode(),
                    e.getErrorCode().getMessage()
            )));
}
```

응답 예시는 아래와 같습니다.

```json
{
  "success": false,
  "data": {
    "code": "USER-003",
    "message": "이미 사용 중인 닉네임입니다."
  }
}
```

### 4. 서비스 적용 범위

사용자 상태를 확인해야 하는 보호 API 중심으로 적용합니다.

- `OnboardingService`: 온보딩 저장, 프로필 이미지 업로드, 온보딩 조회
- `MypageService`: 프로필 수정
- `RecordService`: 기록 저장, 기록 요약, 주간 요약, 주간 기록 조회
- `RankingService`: 내 랭킹 조회
- `WeeklyTierService`: 현재 주간 티어 조회
- `AnalysisService`: 기록 목록 조회, 기록 상세 조회 전 사용자 활성 상태 확인
- `AuthService`: OAuth 매핑은 존재하지만 실제 사용자가 없는 데이터 불일치 처리

### 5. 닉네임 중복 검증 보강

기존에는 프로필 수정에서는 닉네임 중복을 검사했지만,
온보딩 저장에서는 사전 검증이 명확하지 않았습니다.

온보딩 저장에서도 같은 기준을 적용하고,
중복 검증 위치는 `OnboardingService`와 `MypageService`가 아니라
`UserLookupService`로 중앙화합니다.

## 2026-04-30 후속 작업 메모

오늘 작업은 두 브랜치로 분리합니다.

### 먼저 진행할 브랜치

브랜치명: `fix/user-status-auth-guard`

목표는 사용자 상태에 따른 접근 제어와 에러 코드를 정리하는 것입니다.

- 탈퇴 사용자의 기존 refresh token 재사용 시 `USER-002`로 응답하도록 처리합니다.
- 인증 에러 코드에서 중복된 `AC-006`을 제거합니다.
- 예상 티어 계산 API도 정상 사용자만 사용할 수 있도록 `getActiveById` 검증을 추가합니다.
- 단위 테스트와 필요한 API 계약 테스트를 함께 보강합니다.

### 다음에 진행할 브랜치

브랜치명: `security/apple-identity-token-validation`

목표는 Apple 로그인 토큰 검증을 실제 운영 수준으로 강화하는 것입니다.

- Apple identity token 서명을 Apple JWK로 검증합니다.
- issuer가 `https://appleid.apple.com`인지 확인합니다.
- audience가 우리 앱의 Apple client id 또는 bundle id와 일치하는지 확인합니다.
- 만료된 토큰을 거부합니다.
- Apple JWK 조회 실패, 키 회전, 캐시 정책을 테스트합니다.

이 작업은 사용자 상태 버그와 성격이 다르므로 별도 PR로 리뷰합니다.

```text
요청 닉네임이 현재 닉네임과 다르고,
이미 사용 중이면 USER-003으로 거부한다.
```

이렇게 하면 DB unique 제약 위반이 나기 전에
API 계층에서 예측 가능한 실패 응답을 반환할 수 있습니다.

또한 온보딩 저장과 프로필 수정이 같은 메서드를 사용하므로
닉네임 정책이 바뀌어도 한 곳만 수정하면 됩니다.

## 적용 대상 후보

이번 구현에서는 아래 서비스의 사용자 관련 `RuntimeException` 또는 사용자 상태 확인 누락을 점검했습니다.

- `MypageService`
- `OnboardingService`
- `RecordService`
- `RankingService`
- `AuthService`
- `WeeklyTierService`
- `AnalysisService`

추가로 기록, 분석, OAuth 검증처럼 사용자 외 도메인에서 발생하는 일반 예외도
나중에 별도 도메인 예외로 분리할 수 있습니다.

`AnalysisService`의 `"기록을 찾을 수 없습니다."` 예외는 이번 작업에서 바꾸지 않았습니다.
이것은 사용자 도메인이 아니라 기록 도메인의 `RecordNotFoundException`으로 분리하는 것이 더 적절하기 때문입니다.

`ProfileImageStorageService`의 `IllegalArgumentException`도 이번 작업에서 제외했습니다.
프로필 이미지 파일 형식, 확장자, 저장 경로 검증은 사용자 조회 실패와 다른 입력 검증 성격이기 때문입니다.

## 완료 기준

- `UserErrorCode`를 추가합니다.
- `UserException`을 추가합니다.
- `UserLookupService`를 추가합니다.
- `GlobalExceptionHandler`에 `UserException` 처리를 추가합니다.
- 사용자 조회 실패는 `USER_NOT_FOUND`로 통일합니다.
- 탈퇴 사용자 접근은 `WITHDRAWN_USER`로 통일합니다.
- 중복 닉네임은 `DUPLICATED_NICKNAME`으로 통일합니다.
- API 실패 응답 테스트를 추가합니다.

## 정리

이번 리뷰에서 얻은 기준은 아래와 같습니다.

```text
서비스가 커질수록 예외는 메시지가 아니라 타입과 코드로 관리해야 한다.
```

`RuntimeException`을 모두 없애는 것이 목적은 아닙니다.
클라이언트가 처리해야 하는 도메인 오류를 명확한 예외 타입과 에러 코드로 표현하는 것이 목적입니다.

# JSON 요청 파싱 실패 공통 예외 응답 개선

- Status: Archived
- Audience: Engineers
- Source of Truth: No
- Last Reviewed: 2026-06-16

> 이 문서는 JSON 파싱 오류 응답 개선을 검토하던 시점의 기록이다. 현재 기준은 `docs/standards/api-error-response.md`와 실제 `GlobalExceptionHandler` 구현을 우선 확인한다.

## GitHub 이슈 정리

이슈 제목은 아래처럼 정리합니다.

```text
refactor: JSON 요청 파싱 실패 응답에 필드별 오류 정보 추가
```

이슈 라벨은 `refactor`가 있으면 `refactor`,
기본 라벨만 있다면 `enhancement`가 적절합니다.

기존 API가 완전히 실패하는 버그라기보다,
전역 공통 예외 응답의 사용성과 유지보수성을 높이는 작업이기 때문입니다.

## 배경

코드 리뷰에서 `HttpMessageNotReadableException` 발생 시
단순히 `"요청 본문을 읽을 수 없습니다."`만 반환하면
클라이언트가 어떤 필드를 수정해야 하는지 알기 어렵다는 피드백이 있었습니다.

현재 잘못된 JSON 요청이나 DTO 타입 변환 실패 시
전역 예외 핸들러에서 공통적으로 `HttpMessageNotReadableException`을 처리합니다.

예를 들어 러닝 기록 저장 API에서 `OffsetDateTime` 필드에
timezone offset 없이 아래 값을 보내면 DTO 변환 단계에서 실패합니다.

```json
{
  "startAt": "2026-04-14T12:13:09"
}
```

숫자 필드에 문자열이 들어오는 경우도 같은 계열의 문제입니다.

```json
{
  "distanceKm": "abc"
}
```

이 경우 요청은 컨트롤러나 서비스에 도달하기 전에 실패합니다.
즉 도메인 검증 로직에서 직접 던지는 커스텀 예외와 성격이 다릅니다.

## 커스텀 예외와 다른 점

도메인 커스텀 예외는 서버가 요청을 정상적으로 읽고,
서비스 로직 안에서 비즈니스 규칙을 검사하다가 직접 던지는 예외입니다.

```text
요청 JSON 읽기 성공
-> DTO 생성 성공
-> Controller 진입
-> Service 진입
-> 도메인 규칙 검사
-> RecordValidationException 같은 커스텀 예외 발생
```

반면 `HttpMessageNotReadableException`은 요청 JSON을 DTO로 변환하는 단계에서 발생합니다.

```text
요청 JSON 읽기 실패
-> DTO 생성 실패
-> Controller 진입 전 실패
-> Spring/Jackson 예외 발생
```

따라서 이 작업은 특정 도메인의 커스텀 예외 설계가 아니라,
Spring이 던지는 전역 공통 예외를 우리 API 응답 형식에 맞게 가공하는 작업입니다.

## 문제점

- 클라이언트가 잘못된 필드를 알기 어렵습니다.
- DTO validation 실패 응답은 `errors`를 제공하지만, JSON 파싱/타입 변환 실패 응답은 구체 정보가 부족합니다.
- `HttpMessageNotReadableException`은 전역 공통 예외이므로 특정 도메인 메시지로 처리하면 다른 API에 부작용이 생길 수 있습니다.
- Jackson 내부 예외 메시지를 그대로 내려주면 서버 구현 정보가 노출될 수 있습니다.

## 왜 예외 원문을 그대로 반환하지 않는가

리뷰 의견의 핵심은 클라이언트가 수정할 수 있는 정보를 주자는 것입니다.
하지만 `HttpMessageNotReadableException`의 원문 메시지를 그대로 응답에 포함하는 것은 피해야 합니다.

원문에는 아래처럼 서버 구현 세부사항이 포함될 수 있습니다.

- Java 타입명
- Jackson 내부 클래스명
- DTO 패키지 경로
- 역직렬화 내부 경로
- 라이브러리에서 생성한 긴 오류 메시지

이 정보는 프론트 디버깅에는 일부 도움이 될 수 있지만,
공개 API 응답으로는 너무 자세합니다.

따라서 원칙은 아래와 같습니다.

```text
서버 내부 예외 메시지는 그대로 노출하지 않는다.
대신 필드명과 클라이언트가 고칠 수 있는 안전한 메시지를 제공한다.
```

## 목표 응답 구조

기존 `MethodArgumentNotValidException` 응답처럼
공통 실패 응답 안에 `errors`를 포함하는 구조를 유지합니다.

```json
{
  "success": false,
  "data": {
    "code": "BAD_REQUEST",
    "message": "요청 본문 형식이 올바르지 않습니다.",
    "errors": {
      "startAt": "요청 값의 형식이 올바르지 않습니다. 시간 값은 timezone offset을 포함해야 합니다."
    }
  }
}
```

필드명을 알 수 없는 JSON 문법 오류라면 필드별 `errors` 대신
범용 메시지만 반환합니다.

```json
{
  "success": false,
  "data": {
    "code": "BAD_REQUEST",
    "message": "요청 본문 형식이 올바르지 않습니다."
  }
}
```

## 구현 방향

`GlobalExceptionHandler`에서 `HttpMessageNotReadableException`의 cause를 확인합니다.

우선적으로 확인할 후보는 아래입니다.

- `InvalidFormatException`
- `MismatchedInputException`
- 기타 `JsonMappingException`

Jackson mapping exception에서 field path를 얻을 수 있으면
해당 필드를 `errors`에 담습니다.

예상 흐름은 아래와 같습니다.

```text
HttpMessageNotReadableException 발생
-> cause가 JsonMappingException 계열인지 확인
-> path에서 fieldName 추출
-> targetType 또는 실패 필드명을 기준으로 안전한 메시지 선택
-> CommonResponse.failure(...)로 반환
```

타입별 메시지는 너무 세분화하지 않고,
클라이언트가 수정할 수 있는 수준으로만 둡니다.

```text
OffsetDateTime:
시간 값은 timezone offset을 포함해야 합니다. 예: 2026-04-14T12:13:09+09:00

Number:
숫자 형식으로 입력해야 합니다.

Enum:
허용된 값 중 하나로 입력해야 합니다.

Fallback:
요청 값의 형식이 올바르지 않습니다.
```

## 작업 범위

- `GlobalExceptionHandler`의 `HttpMessageNotReadableException` 처리 개선
- Jackson mapping exception에서 가능한 경우 field path 추출
- `OffsetDateTime`, 숫자, enum 등 주요 타입 변환 실패 메시지 정의
- 기존 validation 오류 응답과 동일한 `errors` 구조 유지
- 관련 통합 테스트 추가

## 제외 범위

- 도메인 커스텀 예외 설계
- `UserNotFoundException`, `RecordNotFoundException` 등 비즈니스 예외 리팩터링
- 러닝 기록 저장 정책 변경
- `startAt/endAt` API 계약 변경

## 이번 #19 PR에서 분리한 이유

#19의 핵심은 러닝 기록 저장 API의 시간 필드 계약을
`OffsetDateTime`으로 바꾸는 것입니다.

반면 `HttpMessageNotReadableException`은 전역 공통 예외입니다.
여기서 응답 구조를 바꾸면 로그인, 온보딩, 프로필, 랭킹 등
다른 도메인의 잘못된 JSON 요청 응답에도 영향을 줍니다.

따라서 #19 안에서 급하게 처리하지 않고
별도 이슈로 분리해서 공통 예외 응답 정책으로 정리하는 것이 안전합니다.

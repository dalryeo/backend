# API Error Response Standard

- Status: Draft
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-16

## 목적

이 문서는 달려 백엔드 API가 오류를 응답할 때 지켜야 하는 공통 계약을 정리한다. 기준은 현재 구현된 `CommonResponse`, `GlobalExceptionHandler`, `BusinessException`, `ErrorCode` 구조다.

문서와 코드가 다르면 실제 구현과 테스트를 먼저 확인한다.

## 기본 형태

실패 응답은 `CommonResponse.failure(...)`를 사용한다.

```json
{
  "success": false,
  "data": {
    "code": "BAD_REQUEST",
    "message": "요청 본문 형식이 올바르지 않습니다."
  }
}
```

필드별 오류가 필요한 경우 `data.errors`를 추가한다.

```json
{
  "success": false,
  "data": {
    "code": "BAD_REQUEST",
    "message": "요청 본문 형식이 올바르지 않습니다.",
    "errors": {
      "startAt": "시간 값은 timezone offset을 포함해야 합니다. 예: 2026-04-14T12:13:09+09:00"
    }
  }
}
```

## 도메인 예외

클라이언트가 정상 흐름에서 만날 수 있는 비즈니스 오류는 `BusinessException`과 `ErrorCode`로 표현한다.

- HTTP status는 `ErrorCode.getStatus()`를 따른다.
- 응답 `code`는 `ErrorCode.getCode()`를 따른다.
- 응답 `message`는 예외 메시지를 따른다. 별도 메시지를 넘기지 않으면 `ErrorCode.getMessage()`를 사용한다.

도메인 오류를 단순 `RuntimeException`이나 `IllegalArgumentException`으로 새로 추가하지 않는다. 새 오류가 API 계약에 영향을 주면 도메인별 `ErrorCode`를 먼저 검토한다.

## Validation 오류

`@Valid` 검증 실패는 HTTP 400과 `BAD_REQUEST`로 응답한다.

- `data.message`는 첫 번째 필드 오류 메시지를 사용한다.
- `data.errors`는 `필드명 -> 오류 메시지` 형태의 객체다.
- 같은 필드에 오류가 여러 개 있어도 첫 번째 메시지만 응답한다.

## JSON 본문 파싱 오류

`HttpMessageNotReadableException`은 HTTP 400과 `BAD_REQUEST`로 응답한다.

- 기본 메시지는 `요청 본문 형식이 올바르지 않습니다.`이다.
- Jackson mapping path에서 필드명을 얻을 수 있으면 `data.errors`에 한 개의 필드 오류를 넣는다.
- 필드명을 얻을 수 없으면 `errors`를 넣지 않는다.
- Jackson 원문 메시지, Java 타입명, 패키지 경로, stacktrace는 클라이언트에 노출하지 않는다.

현재 타입별 안내 메시지는 아래 기준을 따른다.

| 대상 타입 | 메시지 |
| --- | --- |
| `OffsetDateTime` | 시간 값은 timezone offset을 포함해야 합니다. 예: 2026-04-14T12:13:09+09:00 |
| `LocalDate` | 날짜 값은 yyyy-MM-dd 형식이어야 합니다. 예: 2001-01-01 |
| 숫자 타입 | 숫자 형식으로 입력해야 합니다. |
| boolean 타입 | true 또는 false로 입력해야 합니다. |
| enum 타입 | 허용된 값 중 하나로 입력해야 합니다. |
| 기타 타입 | 요청 값의 형식이 올바르지 않습니다. |

## IllegalArgumentException

현재 `IllegalArgumentException`은 HTTP 400과 `BAD_REQUEST`로 응답하며 예외 메시지를 그대로 사용한다.

새 API 계약을 만들 때 이 처리에 의존하지 않는다. 클라이언트가 의존해야 하는 오류라면 `BusinessException`으로 바꿔 명시적인 error code를 둔다.

## 로그와 노출 기준

- 4xx 오류는 요청 경로, code, status, exception 이름을 warning 로그로 남긴다.
- 5xx 오류는 같은 맥락과 stacktrace를 error 로그로 남긴다.
- 클라이언트 응답에는 내부 예외 원문과 stacktrace를 넣지 않는다.
- 요청 본문 값이나 토큰 같은 민감한 값은 로그에 남기지 않는다.

## 아직 결정해야 하는 기준

아래 항목은 오래된 troubleshooting 문서에 필요성이 적혀 있지만, 현재 코드 기준으로 완전히 확정된 공통 계약은 아니다.

- 예상 밖 `Exception.class` fallback을 `CommonResponse.failure`로 강제할지 결정해야 한다.
- fallback을 둔다면 HTTP status, code, message를 확정해야 한다. 기존 후보는 `500`, `INTERNAL_SERVER_ERROR`, `서버 오류가 발생했습니다.`이다.
- JSON 파싱 오류에서 여러 필드 오류를 동시에 응답할지, 현재처럼 한 필드만 응답할지 결정해야 한다.
- `IllegalArgumentException`의 메시지를 그대로 노출하는 현재 방식을 유지할지, 공통 안전 메시지로 바꿀지 결정해야 한다.

이 항목을 바꾸면 `GlobalExceptionHandlerTest`와 API 계약 테스트를 함께 갱신한다.

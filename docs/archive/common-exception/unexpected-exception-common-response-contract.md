# 예상 밖 예외의 CommonResponse 계약 통일

- Status: Archived
- Audience: Engineers
- Source of Truth: No
- Last Reviewed: 2026-06-16

> 이 문서는 예상 밖 예외 처리 개선을 검토하던 시점의 계획이다. 현재 기준은 `docs/standards/api-error-response.md`와 실제 `GlobalExceptionHandler` 구현을 우선 확인한다.

관련 이슈: #36

권장 브랜치: `fix/global-error-contract`

## 배경

현재 전역 예외 처리는 `BusinessException`, validation, JSON 파싱, `IllegalArgumentException` 중심이다. 그 외 예상 밖 예외는 Spring 기본 에러 응답으로 떨어질 수 있다.

이 경우 클라이언트는 `CommonResponse` 계약을 기대했지만 다른 응답 형태를 받게 된다. 또한 내부 예외 메시지가 설정에 따라 클라이언트로 노출될 위험도 있다.

## 목표

- 예상 밖 예외도 `CommonResponse.failure` 형태로 응답한다.
- 내부 예외 메시지와 stacktrace는 클라이언트에 직접 노출하지 않는다.
- 서버 로그에는 원인 파악이 가능하도록 stacktrace를 남긴다.
- 도메인 오류는 가능하면 `BusinessException` 계열로 분리한다.

## 제외 범위

- 에러 코드 체계 전체 재설계
- 모든 RuntimeException 일괄 변환
- 관측성 플랫폼 교체
- 프론트 에러 처리 정책 변경

이번 작업은 fallback과 명확한 도메인 예외 1~2개를 정리하는 데 집중한다.

## 설계 방향

`GlobalExceptionHandler`에 마지막 fallback을 추가한다.

```text
@ExceptionHandler(Exception.class)
HTTP 500
code: INTERNAL_SERVER_ERROR
message: 서버 오류가 발생했습니다.
```

이 fallback은 정말 예상하지 못한 예외용이다. 기록이 없는 경우처럼 클라이언트가 정상적으로 만날 수 있는 오류는 별도 `BusinessException`으로 만든다.

예시:

```text
RECORD_NOT_FOUND
HTTP 404
```

## 주요 수정 파일

- `src/main/java/com/ohgiraffers/dalryeo/common/exception/GlobalExceptionHandler.java`
- `src/main/java/com/ohgiraffers/dalryeo/common/exception/*`
- `src/main/java/com/ohgiraffers/dalryeo/analysis/service/AnalysisService.java`
- `src/test/java/com/ohgiraffers/dalryeo/common/exception/GlobalExceptionHandlerTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`

## 실행 체크리스트

- [ ] fallback error code 이름과 메시지를 정한다.
- [ ] `GlobalExceptionHandler`에 `Exception.class` handler를 추가한다.
- [ ] fallback handler에서 `log.error`로 stacktrace를 남긴다.
- [ ] `AnalysisService.getRecordDetail()`의 일반 `RuntimeException`을 도메인 예외로 교체한다.
- [ ] 기록 없음 API 계약 테스트를 추가한다.
- [ ] 예상 밖 예외 fallback 단위 테스트를 추가한다.

## 검증 계획

우선 실행:

```bash
./gradlew test --tests '*GlobalExceptionHandlerTest' --tests '*ApiContractIntegrationTest'
```

전체 회귀:

```bash
./gradlew test --rerun-tasks
```

## 롤백 기준

fallback handler가 `BusinessException`이나 validation 예외를 가로채면 handler 순서와 타입 매칭을 확인한다. fallback 자체를 제거하기보다 구체 예외 handler가 먼저 적용되도록 조정한다.

## 운영 확인

- 500 응답의 body 형태
- Sentry에 stacktrace가 들어오는지
- 클라이언트 응답에 내부 예외 메시지가 노출되지 않는지
- 기존 400/401/403/404 비즈니스 응답 계약이 유지되는지

## 진행 기록

| 날짜 | 상태 | 기록 |
| --- | --- | --- |
| 2026-05-06 | 설계 | #36은 API 응답 계약 변경이므로 단독 브랜치로 처리하기로 결정했다. |

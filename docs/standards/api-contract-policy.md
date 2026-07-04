# API Contract Policy

- Status: Active
- Audience: Backend Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-07-03

## 목적

이 문서는 달려 백엔드에서 무엇을 API 계약으로 보고, `dev`에서 실험한 변경을 `main`으로 승격할 때 어떤 호환성 기준을 적용할지 정한다.

엔드포인트별 상세 예시는 이 문서에 모두 복제하지 않는다. 실제 계약은 Controller, DTO, `CommonResponse`, exception handler, `ErrorCode`, validation, domain 문서, 결정 기록, API 계약 테스트를 함께 보고 판단한다.

## API 계약 범위

아래 항목은 앱 클라이언트나 외부 호출자가 의존할 수 있으므로 API 계약으로 본다.

- HTTP method와 path
- 인증 필요 여부, token 종류, token 목적
- query/path/body request field 이름, 타입, 필수 여부, validation 조건
- response field 이름, 타입, null 가능 여부, enum 값
- `CommonResponse` wrapper 구조와 `success`, `data` 의미
- HTTP status code
- error response shape, error code, message, `data.errors` 구조
- 날짜/시간 포맷과 timezone/offset 해석 방식
- 파일 URL, image path, pagination, 정렬 기준처럼 클라이언트가 화면이나 흐름에 쓰는 값의 의미

## 하위 호환 기준

`dev`와 `main`을 나눠 운영하므로 API 변경은 "새 서버가 배포돼도 기존 앱 클라이언트가 계속 동작하는가"를 기준으로 판단한다.

일반적으로 하위 호환으로 볼 수 있는 변경:

- 기존 field를 유지한 채 optional response field 추가
- 기존 클라이언트가 보내지 않아도 되는 optional request field 추가
- 기존 status code와 error shape를 유지한 내부 로직 수정
- 기존 enum 값의 의미를 유지한 새 enum 값 추가

하위 호환이 깨질 수 있는 변경:

- 기존 request/response field 제거, 이름 변경, 타입 변경
- optional request field를 required로 변경
- response field의 null 가능 여부 변경
- HTTP status code 변경
- `CommonResponse` 구조, error code, message, `data.errors` shape 변경
- 기존 enum 값 제거 또는 의미 변경
- 인증 필요 여부, token 종류, token 목적 변경
- 날짜/시간 포맷 또는 timezone 해석 기준 변경

하위 호환이 불명확하면 하위 호환 영향이 있는 변경으로 다룬다.

## PR 작성 기준

모든 PR은 `.github/PULL_REQUEST_TEMPLATE.md`의 `계약 영향` 섹션을 작성한다.

- `API 계약 영향 범위를 확인함`은 이 PR이 위 API 계약 범위를 건드리는지 확인했다는 뜻이다. 영향 없음이라는 뜻이 아니다.
- `하위 호환 영향 없음`과 `하위 호환 영향 있음` 중 하나를 선택한다.
- `앱 클라이언트 조율 필요 없음`과 `앱 클라이언트 조율 필요` 중 하나를 선택한다.
- `운영 배포 승격: dev -> main` 또는 `hotfix/* -> main` PR에서는 `운영 배포 승격 전 API 계약 테스트 영향 확인함`을 체크한다.

하위 호환 영향이 있거나 앱 클라이언트 조율이 필요하면 `운영 영향` 또는 `리뷰 포인트`에 아래 내용을 적는다.

- 어떤 request/response/status/error 계약이 바뀌는지
- 기존 앱 클라이언트가 깨질 수 있는지
- 앱 클라이언트 변경, 배포 순서, 호환 기간이 필요한지
- `main` 승격 전에 확인해야 할 API 계약 테스트가 무엇인지

## 테스트 기준

API 계약을 바꾸는 PR은 관련 테스트를 같은 PR에서 갱신한다.

- 통합 계약은 `ApiContractIntegrationTest` (`src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`)를 우선 확인한다.
- 공통 오류 응답은 `GlobalExceptionHandlerTest`, `CommonResponse`, `ErrorCode`, `BusinessException` 관련 테스트를 함께 확인한다.
- 날짜/시간 계약은 `docs/standards/time-policy.md`와 관련 결정 기록을 함께 확인한다.
- 문서만 바꾸는 PR이어도 PR 계약 섹션은 작성한다. 문서가 API 계약의 소스 오브 트루스가 될 수 있기 때문이다.

## 오류 응답 기준과의 관계

`docs/standards/api-error-response.md`는 공통 오류 응답 상세 기준을 정리 중인 draft다. 현재 공식 판단은 이 문서의 API 계약 범위와 실제 구현, 테스트를 우선한다.

오류 응답 기준을 `Active`와 `Source of Truth: Yes`로 올릴 때는 이 문서와 충돌하지 않도록 status code, error code, message, `data.errors` shape 기준을 함께 검토한다.

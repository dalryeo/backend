# 온보딩 프로필 이미지 경로 안정화 작업

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

## 문서 목적

이 문서는 2026-04-05에 반영된 온보딩 프로필 이미지 저장/조회 경로 안정화 작업을
빠르게 이해하기 위한 트러블슈팅 문서입니다.

대상 커밋:

- `6a56c00` fix: 프로필 이미지 정리 실패 롤백 전파 보완
- `5099f2` fix: 프로필 이미지 정적 리소스 경로 slash 보완
- `001534f` refactor: 프로필 이미지 경로 정규화 책임 공통화

## 작업 배경

온보딩 프로필 이미지는 서버 로컬 디스크에 저장하고, DB에는 실제 파일 경로가 아니라
애플리케이션이 다시 요청할 수 있는 URL 경로를 저장합니다.

이 구조에서 다음 두 가지 안정화 포인트가 확인되었습니다.

- 파일 정리 실패가 회원 탈퇴나 프로필 수정 같은 핵심 비즈니스 트랜잭션을 함께 롤백시키지 않아야 합니다.
- 정적 리소스 핸들러와 저장 서비스가 같은 경로 규칙을 사용해야 URL prefix와 파일 시스템 경로가 어긋나지 않습니다.

## 커밋별 개발 내용

### 1. `6a56c00` cleanup 실패를 best-effort로 전환

- `ProfileImageStorageService.deleteStoredProfileImage()`에서 파일 삭제 실패를 비즈니스 예외로 다시 던지지 않도록 조정했습니다.
- 삭제 실패는 `warn` 로그로 남기고 호출부 트랜잭션까지 전파하지 않게 했습니다.
- 파일이 아닌 비어 있지 않은 디렉터리 같은 cleanup 실패 상황에서도 예외가 밖으로 전파되지 않는 테스트를 추가했습니다.

핵심 의도:

- 기존 이미지 정리는 부가 작업으로 취급하고, 회원 탈퇴/프로필 수정의 핵심 성공 여부와 분리합니다.

### 2. `5099f2` 정적 리소스 storage URI 끝 slash 보장

- `StaticResourceConfig`에서 업로드 디렉터리를 정적 리소스 location으로 변환할 때 trailing slash가 항상 붙도록 보완했습니다.
- 아직 존재하지 않는 디렉터리 경로를 사용해도 동일한 URI 규칙을 유지하도록 테스트를 추가했습니다.

핵심 의도:

- `file:` URI가 디렉터리로 안정적으로 해석되도록 해 `/profiles/custom/**` 요청 매핑이 환경에 따라 흔들리지 않게 합니다.

### 3. `001534f` 경로 정규화 책임을 설정 객체로 통합

- `ProfileImageStorageProperties`에 아래 파생 메서드를 추가했습니다.
  - `getUploadDirPath()`
  - `getNormalizedUrlPrefix()`
  - `getStorageLocationUri()`
- `StaticResourceConfig`와 `ProfileImageStorageService`가 각자 하던 경로 정규화 로직을 제거하고 위 메서드를 공통 사용하도록 정리했습니다.
- 기존 `StaticResourceConfigTest`는 제거하고, 설정 객체 중심의 `ProfileImageStoragePropertiesTest`로 정규화 규칙을 검증하도록 바꿨습니다.

핵심 의도:

- URL prefix와 storage location 계산 규칙을 한 곳에서 관리해 중복 구현과 향후 불일치 위험을 줄입니다.

## 영향 범위

- 프로필 이미지 업로드 후 DB에 저장되는 URL 경로 생성 방식
- `/profiles/custom/**` 정적 리소스 서빙 경로 해석
- 프로필 수정/회원 탈퇴 시 기존 커스텀 이미지 cleanup 처리
- 프로필 이미지 설정값 변경 시 경로 정규화 규칙 적용 범위

## 테스트 포인트

- cleanup 대상 파일 삭제가 실패해도 서비스 예외가 호출부로 전파되지 않는지
- 업로드 디렉터리 경로가 존재하지 않아도 정적 리소스 location URI 끝 `/`가 유지되는지
- `urlPrefix`가 공백, leading slash 없음, trailing slash 포함 케이스에서 모두 `/profiles/custom` 규칙으로 정규화되는지

## 운영/리뷰 메모

- cleanup 실패는 롤백 대신 로그 추적으로 전환됐으므로 운영 로그에서 파일 정리 실패 누적 여부를 확인할 필요가 있습니다.
- `ProfileImageStorageProperties`가 경로 규칙의 단일 진입점이 되었으므로 관련 설정 변경은 이 객체 기준으로 검토하면 됩니다.

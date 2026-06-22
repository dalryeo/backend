# Profile Image Storage MVP

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-20

## 결정

달려 백엔드는 MVP 단계에서 프로필 이미지를 아래 방식으로 관리한다.

- 티어 기본 이미지는 백엔드 정적 리소스로 제공한다.
- 사용자 커스텀 프로필 이미지는 서버 로컬 파일 저장소에 저장한다.
- 운영 배포에서는 업로드 디렉터리를 persistent volume으로 유지하는 것을 전제로 한다.
- DB에는 이미지 바이너리가 아니라 다시 요청 가능한 경로 또는 URL만 저장한다.
- 외부 오브젝트 스토리지, CDN, 이미지 리사이징 파이프라인은 현재 기본 구조로 두지 않는다.

## 배경

현재 이미지 사용 범위는 티어 기본 이미지와 사용자 커스텀 프로필 이미지가 중심이다. 이 단계에서 S3, R2, Cloudflare Images 같은 외부 스토리지를 먼저 붙이면 운영 복잡도가 커진다.

반대로 티어 기본 이미지를 앱 번들에만 두면 이미지 교체나 티어 메타데이터 변경 때 앱 배포가 필요해진다. 따라서 MVP에서는 서버가 이미지 기준을 소유하고, 확장 필요가 생길 때 외부 스토리지로 옮기는 방식을 선택한다.

## 선택한 방식

티어 기본 이미지는 `classpath:/static/profiles/tiers/` 아래 정적 리소스로 제공한다. `tier.default_profile_image`에는 전체 URL이 아니라 경로를 저장한다.

커스텀 프로필 이미지는 `app.profile-image.upload-dir` 아래 저장한다. 외부에 노출되는 URL prefix는 `app.profile-image.url-prefix`를 기준으로 만든다.

현재 기본값은 아래와 같다.

```text
app.profile-image.upload-dir=uploads/profile-images
app.profile-image.url-prefix=/profiles/custom
```

업로드된 파일명은 원본 파일명을 그대로 쓰지 않고 서버가 새로 만든다. 파일명 충돌, 원본 파일명 노출, 특수문자 처리 문제를 줄이기 위해서다.

## 응답 계약

현재 온보딩 조회 응답은 표시용 값과 원천 값을 함께 제공한다.

- `displayProfileImage`: 화면에 표시할 최종 이미지
- `customProfileImage`: 사용자가 직접 설정한 이미지
- `defaultProfileImage`: 현재 티어 기준 기본 이미지

커스텀 이미지가 있으면 `displayProfileImage`는 커스텀 이미지를 사용한다. 커스텀 이미지가 없으면 현재 티어 기본 이미지를 사용한다.

과거 결정 원문에는 `displayProfileImage` 하나만 반환하는 방향이 적혀 있었지만, 현재 구현 기준은 위 세 값을 제공하는 계약이다. 문서를 판단할 때는 현재 구현과 API 계약을 우선한다.

## 주요 판단

- 기본 이미지는 사용자별 저장값이 아니라 티어 메타데이터다.
- 커스텀 이미지만 사용자 저장값으로 본다.
- 기본 이미지는 삭제 대상이 아니다.
- 서버가 관리하는 커스텀 이미지만 교체 시 best-effort로 삭제한다.
- `image_type`, `image_name` 같은 컬럼 분리는 현재 도입하지 않는다.
- 서버가 여러 대로 늘어나거나 이미지 트래픽이 커지면 외부 스토리지 전환을 별도 결정으로 다룬다.

## 운영 영향

로컬 파일 저장은 단일 서버 MVP에는 단순하지만, 배포 환경에서 업로드 디렉터리가 유지되지 않으면 사용자 이미지가 사라질 수 있다. 운영에서는 `PROFILE_IMAGE_UPLOAD_DIR`이 persistent volume에 연결되는지 확인해야 한다.

외부 스토리지로 전환할 때는 기존 `user.profile_image` 값의 URL 호환성, 정적 리소스 서빙 경로, 이전 파일 마이그레이션 방식을 함께 결정해야 한다.

## 구현 앵커

- `src/main/resources/application.yml`
- `src/main/java/com/ohgiraffers/dalryeo/config/ProfileImageStorageProperties.java`
- `src/main/java/com/ohgiraffers/dalryeo/config/StaticResourceConfig.java`
- `src/main/java/com/ohgiraffers/dalryeo/onboarding/service/ProfileImageStorageService.java`
- `src/main/java/com/ohgiraffers/dalryeo/onboarding/service/OnboardingService.java`
- `src/main/java/com/ohgiraffers/dalryeo/onboarding/controller/OnboardingController.java`
- `src/test/java/com/ohgiraffers/dalryeo/onboarding/service/ProfileImageStorageServiceTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`

## 보관된 원문

- `docs/archive/profile-image/profile-image-storage-mvp-original.md`

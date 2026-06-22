# 프로필 이미지 업로드 검증 강화

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

관련 이슈: #38

권장 브랜치: `fix/profile-image-upload-validation`

## 배경

현재 프로필 이미지 업로드는 확장자와 content type을 중심으로 검증한다. path traversal 방어와 관리 URL prefix 검증은 들어가 있지만, 파일 자체가 실제 이미지인지 확인하는 검증은 약하다.

공개 업로드 기능은 악성 파일, 큰 파일, 저장소 고갈, 재배포 시 파일 유실 같은 운영 리스크가 있다.

## 목표

- 크기 초과 파일을 명확히 거부한다.
- 이미지가 아닌 파일의 확장자 위장을 거부한다.
- 저장 실패와 삭제 실패 로그/응답 정책을 명확히 한다.
- 운영 배포 환경에서 로컬 디스크 저장의 지속 가능성을 확인한다.

## 제외 범위

- Azure Blob/S3 전환 구현
- CDN 도입
- 이미지 썸네일 생성
- 기존 저장 이미지 일괄 마이그레이션

이번 작업은 업로드 검증의 MVP 안전성을 높이는 데 집중한다.

## 설계 방향

Spring multipart size limit을 설정으로 명시한다.

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 5MB
      max-request-size: 5MB
```

파일 검증은 세 단계로 나눈다.

```text
1. MultipartFile 비어 있음 확인
2. 확장자 allowlist 확인
3. 실제 이미지 디코딩 가능 여부 확인
```

실제 이미지 확인은 `ImageIO.read` 또는 별도 이미지 라이브러리로 처리한다. animated gif/webp 지원 여부는 현재 클라이언트 요구와 Java 기본 지원 범위를 확인한 뒤 결정한다.

## 주요 수정 파일

- `src/main/java/com/ohgiraffers/dalryeo/onboarding/service/ProfileImageStorageService.java`
- `src/main/resources/application.yml`
- `src/test/java/com/ohgiraffers/dalryeo/onboarding/service/ProfileImageStorageServiceTest.java`
- `src/test/java/com/ohgiraffers/dalryeo/integration/ApiContractIntegrationTest.java`

## 실행 체크리스트

- [ ] 운영에서 허용할 최대 이미지 크기를 정한다.
- [ ] multipart max file/request size를 설정한다.
- [ ] 실제 이미지 디코딩 검증을 추가한다.
- [ ] 확장자 위장 파일 테스트를 추가한다.
- [ ] 너무 큰 파일 요청의 응답 계약을 확인한다.
- [ ] 로컬 디스크 저장이 Azure Container App 재배포에서 유지되는지 확인한다.
- [ ] 유지되지 않는다면 외부 스토리지 전환 이슈를 별도로 만든다.

## 검증 계획

우선 실행:

```bash
./gradlew test --tests '*ProfileImageStorageServiceTest' --tests '*ApiContractIntegrationTest'
```

전체 회귀:

```bash
./gradlew test --rerun-tasks
```

수동 확인:

```text
정상 jpg/png 업로드
확장자만 png인 텍스트 파일 업로드 거부
크기 초과 파일 업로드 거부
업로드 후 반환 URL 접근 가능
```

## 롤백 기준

실제 이미지 검증으로 정상 이미지가 과도하게 거부되면 지원 포맷 범위를 확인한다. 특히 gif/webp는 Java 기본 디코더 지원이 제한될 수 있으므로, 지원 포맷을 줄이거나 라이브러리 도입을 별도 판단한다.

## 운영 확인

- 업로드 실패율
- 저장 디렉터리 용량 증가
- 삭제 실패 WARN 로그
- 프로필 이미지 URL 404 발생 여부

## 진행 기록

| 날짜 | 상태 | 기록 |
| --- | --- | --- |
| 2026-05-06 | 설계 | #38은 런칭 후 개선 가능하지만 공개 업로드 리스크가 있어 별도 브랜치로 추적하기로 결정했다. |

# Decisions Log

- Status: Active
- Audience: Engineers
- Source of Truth: Yes
- Last Reviewed: 2026-06-20

## 이 목차의 범위

결정 이유를 남겨야 하는 문서를 연결한다. "무엇을 해야 하는가"보다 "왜 이 선택을 했는가"가 중심이면 이쪽이다.

## 현재 연결된 문서

- [Apple Identity Token Validation](../decisions/apple-identity-token-validation.md): Apple OAuth identity token을 서버에서 검증하기로 한 결정
- [Flyway Migration System](../decisions/flyway-migration-system.md): Flyway를 DB schema 변경 이력 기준으로 채택한 결정
- [JWT Token Purpose Separation](../decisions/jwt-token-purpose-separation.md): access token과 refresh token 용도를 JWT claim으로 분리한 결정
- [Profile Image Storage MVP](../decisions/profile-image-storage-mvp.md): MVP 단계의 기본 이미지와 커스텀 프로필 이미지 저장 방식을 정한 결정
- [Running Record Time Contract](../decisions/running-record-time-contract.md): 러닝 기록 저장 API의 offset 포함 시간 계약을 정한 결정
- [Weekly Ranking Read Model](../decisions/weekly-ranking-read-model.md): 주간 랭킹을 `weekly_user_stats` read model 기준으로 조회하기로 한 결정

## 이관 대기 주제

- 프로필 이미지 외부 스토리지 전환 여부
- 랭킹 cache 또는 snapshot 도입 여부

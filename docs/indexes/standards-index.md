# Engineering Standards

- Status: Active
- Audience: Engineers
- Source of Truth: Yes
- Last Reviewed: 2026-06-20

## 이 목차의 범위

달려 백엔드를 바꿀 때 반복해서 적용할 규칙을 연결한다. 특정 기능 설명보다 "앞으로도 이렇게 하자"에 가까운 문서가 여기에 온다.

## 현재 연결된 문서

- [Dalryeo Documentation Rules](../standards/documentation-system.md): 문서 위치 선택, 목차 관리, 이관 방식
- [Git Branch Strategy](../standards/git-branch-strategy.md): 브랜치 수명주기와 이름 규칙
- [Git Commit Convention](../standards/git-commit-convention.md): 커밋 타입과 메시지 규칙
- [Time Policy](../standards/time-policy.md): `Asia/Seoul`, 주간 기준일, offset 포함 기록 시간 계약 기준

## 정리 예정 기준

아래 주제는 standards 문서로 정리할 후보이다. 실제 문서 생성이나 기존 문서 이동은 별도 승인 후 진행한다.

- [API Error Response Standard](../standards/api-error-response.md): `CommonResponse`, 예외 응답, validation 오류, JSON 파싱 오류 기준 정리 예정
- [Testing Policy](../standards/testing-policy.md): `scripts/test-local.sh`, 특정 테스트 실행, 전체 회귀 테스트 기준
- [Database Migration Policy](../standards/database-migration-policy.md): DB 변경 기준, Flyway migration 작성 규칙, rollback 판단 기준 정리 예정

## 제외 기준

- 운영 중 따라 할 절차는 `runbooks`에 둔다.
- record, ranking, tier 같은 도메인 규칙은 `domains`에 둔다.
- 특정 선택을 한 이유와 분석 결과는 `decisions`에 둔다.
- 현재 기준이 아닌 보관 문서는 `archive`에 둔다.

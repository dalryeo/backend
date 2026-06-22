# Domain Notes

- Status: Active
- Audience: Engineers
- Source of Truth: Yes
- Last Reviewed: 2026-06-16

## 이 목차의 범위

달려의 도메인 모델, 책임 경계, 핵심 규칙을 찾기 위한 목차다. API 모양보다 각 도메인이 무엇을 소유하고 무엇을 소유하지 않는지를 우선한다.

## 현재 연결된 문서

- [Record](../domains/record.md): 러닝 기록, 주간 집계, record outbox 경계
- [Ranking](../domains/ranking.md): 주간 순위 정의, 랭킹 대상, 내 랭킹 규칙
- [Tier](../domains/tier.md): 티어 점수, 메타데이터, 주간 티어 스냅샷 정책
- [Onboarding](../domains/onboarding.md): 초기 프로필, 프로필 이미지, 예상 티어 경계
- [Auth](../domains/auth.md): Apple OAuth, JWT, refresh token, 사용자 생명주기
- [Mypage](../domains/mypage.md): 온보딩 이후 프로필 수정, 닉네임 검증, 이미지 참조 갱신

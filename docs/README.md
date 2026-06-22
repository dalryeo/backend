# Dalryeo Docs Hub

- Status: Active
- Audience: Engineers
- Source of Truth: Yes
- Last Reviewed: 2026-06-20

## 이 문서가 하는 일

달려 백엔드는 출시 전 안정화 문서가 빠르게 쌓인 상태다. DB 전환, record 저장 계약, weekly stats, ranking, onboarding, tier처럼 서로 영향을 주는 문서가 여러 경로에 흩어져 있다.

이 README는 새 문서를 어디에 둘지 먼저 결정하는 기준점이다. 기존 문서를 옮길 때는 현재 기준 문서, 결정 기록, 실행 절차, 보관 자료를 구분한다.

## 어디부터 볼까

- 개발 규칙, 테스트 방식, 커밋/브랜치 기준: `indexes/standards-index.md`
- Spring 계층, DB, 배포, 집계 흐름: `indexes/architecture-index.md`
- record, ranking, tier, onboarding 같은 업무 규칙: `indexes/domains-index.md`
- 운영자가 순서대로 따라야 하는 절차: `indexes/runbooks-index.md`
- 왜 그렇게 결정했는지 남긴 분석과 판단: `indexes/decisions-index.md`
- 더 이상 현재 기준은 아니지만 보관할 자료: `indexes/archive-index.md`
- Codex가 작업 중 만든 초안과 계획: `indexes/working-docs-index.md`

## 분류 기준

- `docs/standards/`: 앞으로도 반복 적용할 규칙을 둔다.
- `docs/architecture/`: 시스템이 어떻게 연결되는지 설명한다.
- `docs/domains/`: 도메인 불변식, 검증, 집계 영향을 정리한다.
- `docs/runbooks/`: 상황이 생겼을 때 실행할 순서와 확인 지점을 적는다.
- `docs/decisions/`: 대안, 실험 결과, 선택 이유를 남긴다.
- `docs/archive/`: 현재 판단에는 쓰지 않지만 삭제하기 아까운 문서를 보관한다.

## 작업 문서

`docs/superpowers/`는 Codex 작업 중 생긴 초안과 계획을 둔다. 여기 있는 문서는 기본적으로 작업 메모다. 운영 기준이나 API 계약으로 계속 남겨야 하면 위 분류 중 하나로 옮긴다.

## 이관 상태

`docs/pre-ops/`는 새 구조로 이관했다.

새 기준 문서는 위 목차에서 찾는다. 예외 경로를 만들 필요가 있으면 먼저 `standards/documentation-system.md`를 갱신한다.

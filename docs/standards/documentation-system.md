# Dalryeo Documentation Rules

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-22

## 목적

이 문서는 달려 문서를 새 위치로 정리할 때의 판단 기준이다. 목표는 문서를 예쁘게 분류하는 것이 아니라, 다음 작업자가 필요한 기준을 덜 헤매고 찾게 만드는 것이다.

## 위치를 고르는 질문

문서를 옮기거나 새로 만들 때 아래 질문을 먼저 본다.

- "앞으로 지켜야 하는 규칙인가?" 그러면 `docs/standards/`
- "시스템이 어떻게 흘러가는지 설명하는가?" 그러면 `docs/architecture/`
- "record, ranking, tier처럼 업무 규칙을 정의하는가?" 그러면 `docs/domains/`
- "장애, 배포, 마이그레이션 때 따라 할 순서인가?" 그러면 `docs/runbooks/`
- "대안 비교, 실험, 결정 이유를 남기는가?" 그러면 `docs/decisions/`
- "지금 기준은 아니지만 나중에 확인할 수 있어야 하는가?" 그러면 `docs/archive/`

## 문서 상단 정보

새 공식 문서는 아래 정보를 문서 상단에 둔다.

```markdown
- Status: Active
- Audience: Engineers
- Source of Truth: Yes
- Last Reviewed: YYYY-MM-DD
```

`Source of Truth: No`인 문서는 참고 자료나 작업 메모로만 본다. 코드나 운영 판단의 기준으로 쓰려면 먼저 공식 문서로 승격한다.

## 목차 관리

- 새 공식 문서는 해당 `docs/indexes/` 문서에 한 줄로 연결한다.
- 목차에는 문서의 결론을 복사하지 않는다.
- 옮긴 문서의 기존 링크가 깨지는지 확인한다.
- 같은 주제를 다루는 옛 문서가 있으면 `decisions`나 `archive`로 보낼지 함께 판단한다.
- `standards`, `architecture`, `domains`, `runbooks`, `decisions`, `archive` 아래 공식 문서는 반드시 `docs/indexes/` 문서 중 하나에서 연결되어야 한다.

## Codex 작업 문서

`docs/superpowers/`는 작업 중인 설계와 실행 계획을 두는 장소다. 완료된 작업에서 남겨야 할 기준은 이 위치에 계속 두지 않고 `standards`, `architecture`, `domains`, `runbooks`, `decisions` 중 알맞은 곳으로 옮긴다.

`docs/superpowers/`는 공식 문서 lint 대상이 아니다. 이 영역의 문서는 작업 메모로 보고, 공식 기준이 필요해질 때만 위 공식 카테고리로 승격한다.

## 이관 방식

- 기존 문서는 파일 하나씩 목적을 확인하고 옮긴다.
- 이동 전 사용자 승인을 받는다.
- 이동하면서 본문을 고칠 필요가 있으면 이동과 수정 범위를 분리해서 보고한다.
- 기존 `pre-ops`/`post-ops` 이름은 새 문서에서는 쓰지 않는다. 내용 성격에 맞춰 새 위치를 고른다.

## 검증

문서 구조 변경 후에는 아래 명령으로 새 공식 문서 영역을 검증한다.

```bash
bash scripts/docs-lint.sh
```

현재 검증 기준은 아래와 같다.

- 필수 docs 디렉터리와 index 파일이 있어야 한다.
- 공식 문서 영역의 빈 파일은 실패한다.
- 공식 문서에는 `Status`, `Audience`, `Source of Truth`, `Last Reviewed`가 있어야 한다.
- 공식 문서와 `AGENTS.md`의 로컬 markdown 링크는 깨지면 안 된다.
- 공식 문서 영역의 파일은 `docs/indexes/` 문서 중 하나에서 연결되어야 한다.

`docs/superpowers/`는 위 공식 문서 검증에서 제외한다.

기존 이관 대기 문서에는 절대 경로 링크 같은 오래된 흔적이 남아 있을 수 있다. 그 정리는 해당 파일을 실제로 이관할 때 함께 처리한다.

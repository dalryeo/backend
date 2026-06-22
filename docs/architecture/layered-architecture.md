# Layered Architecture

- Status: Active
- Audience: Engineers, Codex
- Source of Truth: Yes
- Last Reviewed: 2026-06-15

## 목적

이 문서는 달려 백엔드가 현재 어떤 아키텍처와 패키지 구조로 되어 있는지 설명한다. 새 구조를 제안하는 문서가 아니라, 현재 코드 기준의 작업 경계를 정리하는 문서다.

## 현재 구조

달려 백엔드는 Java/Spring Boot 기반의 단일 백엔드이며, 도메인별 패키지 안에 Controller, Service, Repository, Entity, DTO를 두는 레이어드 아키텍처를 사용한다.

대표 구조는 아래와 같다.

```text
com.ohgiraffers.dalryeo.<area>/
├── controller/
├── service/
├── repository/
├── entity/
├── dto/
└── exception/
```

모든 영역이 같은 하위 패키지를 전부 갖지는 않는다. 예를 들어 `ranking`은 조회 중심이라 별도 entity/repository가 없고, `record`는 outbox 하위 패키지를 추가로 가진다.

## 레이어 책임

| 레이어 | 책임 |
| --- | --- |
| Controller | HTTP 요청/응답 처리, 인증 사용자 식별, DTO 변환 |
| Service | 비즈니스 규칙, 트랜잭션, 도메인 검증, 다른 서비스 조합 |
| Repository | JPA 기반 영속성 접근 |
| Entity | DB에 저장되는 상태와 최소 도메인 행위 |
| DTO | 외부 API 요청/응답 계약 |
| Exception | 도메인별 예외와 에러 코드 |

공통 응답과 공통 예외는 `common` 패키지를 우선 확인한다.

- `common/CommonResponse.java`
- `common/exception/BusinessException.java`
- `common/exception/ErrorCode.java`
- `common/exception/GlobalExceptionHandler.java`

## 주요 패키지

- `auth`: 인증, JWT, Apple identity token 검증
- `onboarding`: 초기 사용자 정보, 예상 티어, 기본 프로필 이미지
- `record`: 러닝 기록, 주간 요약, weekly stats, record outbox
- `ranking`: 주간 랭킹 조회
- `weeklytier`: 주간 티어 확정과 스케줄링
- `tier`: 티어 메타데이터, 점수 계산, 현재 주간 티어 조회
- `analysis`: 기록 분석과 상세 조회
- `common`: 공통 응답, 예외, 시간 기준
- `config`: 애플리케이션 설정

## 도메인 규칙 위치

이 문서는 패키지 구조와 레이어 책임만 다룬다. record 저장, weekly stats, ranking, tier, onboarding 같은 업무 규칙과 변경 영향은 `docs/domains/`에서 관리한다.

## 변경 기준

- Controller에는 비즈니스 규칙을 오래 두지 않는다.
- 검증과 계산은 Service 또는 전용 validator/calculator로 둔다.
- Repository는 DB 접근에 집중한다.

# Production 로그, 에러, Actuator 노출 축소와 CORS 정책 명시

- Status: Archived
- Audience: Engineers, Codex
- Source of Truth: No
- Last Reviewed: 2026-06-20

관련 이슈: #34, #37

권장 브랜치: `fix/production-security-config`

## 배경

현재 기본 설정은 운영 기준으로 노출이 크다.

- SQL 로그 출력
- Hibernate Binder TRACE
- 애플리케이션 DEBUG 로그
- error message always
- actuator health details always
- metrics/prometheus endpoint 공개
- CORS 정책 미명시

이 작업은 애플리케이션 기능을 바꾸기보다 운영 노출면을 줄이는 작업이다. CORS도 같은 운영 노출 정책에 속하므로 함께 검토한다.

## 목표

- production profile에서 민감하거나 과도한 로그를 끈다.
- 외부에 공개되는 actuator endpoint를 최소화한다.
- 예외 응답이 내부 메시지나 stacktrace를 노출하지 않게 한다.
- 운영 허용 origin만 CORS로 명시한다.

## 제외 범위

- Spring Security 인증 구조 전환
- 관리자용 actuator 인증 체계 구현
- 로그 수집 인프라 교체
- WAF 또는 API Gateway 설정 변경

Spring Security 도입이 필요해질 정도로 범위가 커지면 CORS는 별도 브랜치로 분리한다.

## 설계 방향

`application.yml`의 기본값은 운영에 가까운 안전한 값으로 낮추고, 개발 편의 설정은 별도 profile로 분리한다.

권장 구조:

```text
application.yml
공통 기본값

application-prod.yml
운영 노출 최소화

application-local.yml
로컬 디버깅 편의 설정
```

CORS는 환경변수 기반 allowlist로 관리한다.

```yaml
app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:}
```

모바일 앱만 직접 호출한다면 CORS는 브라우저 보안 정책에만 영향을 준다. 그래도 웹 클라이언트가 붙을 가능성과 임시 전체 허용을 막기 위해 정책을 명시한다.

## 주요 수정 파일

- `src/main/resources/application.yml`
- `src/main/resources/application-prod.yml`
- `src/main/resources/application-local.yml`
- `src/main/java/com/ohgiraffers/dalryeo/config/*Cors*`
- CORS/API 설정 테스트

## 실행 체크리스트

- [ ] 현재 운영에서 사용하는 profile 값을 확인한다.
- [ ] prod profile에서 `show-sql=false`로 설정한다.
- [ ] Hibernate SQL/Binder 로그를 INFO 또는 OFF로 낮춘다.
- [ ] `server.error.include-message`를 운영에서 `never` 또는 필요한 최소값으로 낮춘다.
- [ ] actuator 공개 endpoint를 `health,info` 중심으로 줄인다.
- [ ] `health.show-details`를 운영에서 `never` 또는 `when_authorized`로 낮춘다.
- [ ] CORS allowlist 설정 클래스를 추가한다.
- [ ] 허용 origin과 미허용 origin 테스트를 추가한다.

## 검증 계획

설정 바인딩 또는 API 테스트:

```bash
./gradlew test --tests '*ApiContractIntegrationTest'
```

전체 회귀:

```bash
./gradlew test --rerun-tasks
```

수동 확인이 필요한 항목:

```text
prod profile로 기동했을 때 SQL bind parameter가 로그에 남지 않는지 확인
/actuator/health 외 endpoint가 외부에서 접근 가능한지 확인
허용되지 않은 Origin의 preflight 요청이 차단되는지 확인
```

## 롤백 기준

운영 배포 후 앱 API 호출이 CORS 문제로 막히면 허용 origin 설정을 먼저 수정한다. 전체 origin 허용은 임시 조치로만 사용하고, 원인을 확인한 뒤 바로 좁힌다.

로그 축소로 장애 분석이 어렵다면 특정 logger만 일시적으로 올리고, 기본 prod 설정은 유지한다.

## 운영 확인

- 배포 후 4xx CORS 관련 오류
- actuator endpoint 외부 접근 가능 여부
- Sentry event에 Authorization, Cookie, request body가 남지 않는지
- SQL bind parameter 로그 노출 여부

## 진행 기록

| 날짜 | 상태 | 기록 |
| --- | --- | --- |
| 2026-05-06 | 설계 | #34, #37을 운영 노출면 묶음으로 처리하기로 결정했다. |

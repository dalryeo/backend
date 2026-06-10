# Hikari 커넥션 풀 설정 완화 설계

## 배경

2026-06-10 09:54~10:13 KST 사이에 `backend-api-dev`에서 DB 연결 관련 오류가 반복됐다.

확인된 주요 로그는 다음과 같다.

- `HikariPool-1 - Connection is not available, request timed out after 30000ms`
- `FATAL: (ECHECKOUTTIMEOUT) unable to check out connection from the pool after 15000ms in Session mode`
- `HikariPool-1 - Failed to validate connection ... This connection has been closed. Possibly consider using a shorter maxLifetime value.`

로그상 애플리케이션 비즈니스 규칙 실패보다는 PostgreSQL 또는 DB 앞단 connection pooler의 연결 가용성 문제가 더 가능성이 높다.

## 목표

앱 replica 하나가 DB 연결을 과하게 오래 잡지 않도록 Hikari 설정을 보수적으로 낮춘다. DB 또는 pooler가 먼저 닫은 연결은 더 빠르게 정리하고, 연결 획득 대기 시간이 길어져 API 응답 지연으로 번지는 상황을 줄인다.

## 변경 범위

이번 변경은 `src/main/resources/application.yml`의 `spring.datasource.hikari` 설정과 `record.outbox.scheduler` 기본값만 수정한다.

서비스 코드, DB 스키마, 배포 workflow는 이번 범위에서 변경하지 않는다.

## Hikari 설정 방향

- `maximum-pool-size` 기본값을 `5`에서 `3`으로 낮춘다.
- `minimum-idle` 기본값은 `1`을 유지한다.
- `connection-timeout` 기본값을 `30000ms`에서 `20000ms`로 낮춘다.
- `idle-timeout` 기본값을 `300000ms`에서 `60000ms`로 낮춘다.
- `max-lifetime` 기본값을 `600000ms`에서 `240000ms`로 낮춘다.
- `keepalive-time` 기본값을 `60000ms`로 추가한다.
- `validation-timeout` 기본값을 `5000ms`로 추가한다.

각 값은 환경변수로 덮어쓸 수 있게 둔다. 운영 관측 결과에 따라 Azure Container App 환경변수만 조정해 재배포 부담을 줄이기 위함이다. `minimum-idle`은 1개 유휴 연결을 유지해 간헐적 요청에서 매번 새 DB 커넥션을 만드는 부담을 줄인다.

## Outbox 스케줄러 설정 방향

scheduled task의 DB connection timeout이 반복됐으므로, outbox 스케줄러가 DB를 두드리는 빈도와 한 번에 처리하는 작업량도 낮춘다.

- `fixed-delay-ms` 기본값을 `5000ms`에서 `30000ms`로 늘린다.
- `batch-size` 기본값은 `20`을 유지한다.

이 변경은 DB connection pool이 불안정할 때 scheduled task가 반복적으로 연결을 요구하는 압력을 줄인다. batch size는 기존 처리량을 유지해 러닝 기록 저장 후 주간 집계와 랭킹 반영이 과하게 밀리는 위험을 낮춘다.

## 기대 효과

앱 replica가 DB 앞단 pooler에 동시에 요구할 수 있는 최대 연결 수를 줄인다. 유휴 연결을 오래 잡고 있는 시간을 줄이고, 닫힌 연결을 더 빨리 감지해 교체한다.

## 운영 영향

DB는 더 보호되지만, 순간적으로 DB 작업이 몰릴 때 앱 내부에서 connection 대기가 늘 수 있다. 적용 후에는 Grafana에서 DB connection timeout 로그, API 응답 시간, `HikariPool` 관련 로그가 줄었는지 확인한다.

## 관측 기준

Grafana에서는 사용자 영향과 직접 원인을 나눠서 본다.

직접 원인 확인에는 DB connection timeout 로그와 `HikariPool` 로그를 우선한다. 이번 장애의 핵심 증상이 connection checkout 실패였기 때문에, API 응답 시간보다 더 직접적인 신호다.

사용자 영향 확인에는 API 응답 시간을 본다. 특히 평균보다 p95 또는 p99 응답 시간이 더 중요하다. scheduled task에서만 오류가 반복되면 API 응답 시간은 크게 튀지 않을 수 있으므로, API 응답 시간 하나만으로 해결 여부를 판단하지 않는다.

배포 전 API 응답 시간 기준선은 다음과 같다.

- 기간: 2026-06-09 15:40 ~ 2026-06-10 15:40 KST
- `avg` 평균: 0.0376초, 약 37.6ms
- `p95` 평균: 0.0436초, 약 43.6ms
- `max` 평균: 0.0507초, 약 50.7ms
- `p95` 최고: 0.0622초, 약 62.2ms, 2026-06-10 14:10 KST
- `max` 최고: 0.0672초, 약 67.2ms, 2026-06-10 15:20 KST

장애 알림이 발생한 2026-06-10 09:50~10:10 KST 근처의 `p95`는 38.7~43.0ms 수준이었다. 따라서 확인된 API 응답 시간 기준으로는 사용자 요청 전반의 지연 악화는 뚜렷하게 보이지 않는다. 다만 failed request와 exception alert는 별도 원인도 섞일 수 있으므로, 배포 후 1주일 동안 connection timeout 로그와 함께 추적한다.

## 검증

설정 파일 변경이므로 별도 도메인 테스트는 추가하지 않는다. 변경 후 Spring Boot 설정 로딩과 기존 테스트 회귀를 확인한다.

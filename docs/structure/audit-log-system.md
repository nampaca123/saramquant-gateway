# Audit Log & Visitor Tracking System

## 개요

Gateway/Calc 양쪽 서버의 모든 API 활동과 파이프라인 실행을 단일 `audit_log` 테이블로 기록하며, IP 지리 추적을 통해 방문자 분석까지 수행하는 시스템.

---

## DB 스키마

### `audit_log`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | UUID PK | 자동 생성 |
| server | TEXT | `gateway` / `calc` |
| action | TEXT | `API` / `PIPELINE` |
| method | TEXT | HTTP method 또는 pipeline command |
| path | TEXT | API path 또는 pipeline name |
| ip_geolocation_id | UUID FK | ip_geolocations 참조 (nullable) |
| user_id | UUID | JWT에서 추출한 사용자 ID (nullable) |
| status_code | INT | HTTP 응답 코드 |
| duration_ms | BIGINT | 처리 시간 (ms) |
| metadata | JSONB | 파이프라인 상세 (steps, coverage 등) |
| created_at | TIMESTAMPTZ | 생성 시각 |

**인덱스**: server, created_at, user_id, action, visit_stats 복합, date expression

### `ip_geolocations`

| 컬럼 | 타입 | 설명 |
|---|---|---|
| id | UUID PK | 자동 생성 |
| ip_hash | TEXT UNIQUE | HMAC-SHA256 해시 |
| ip_masked | TEXT | 마스킹된 IP (X.X.\*.\*) |
| country~longitude | TEXT/NUMERIC | 지리 정보 |
| network_provider | TEXT | 네트워크 제공자 |
| created_at, updated_at | TIMESTAMPTZ | 시간 정보 |

---

## 아키텍처

### Gateway (Kotlin/Spring Boot)

```
infra/log/
├── entity/          AuditLog, IpGeolocation JPA 엔티티
├── repository/      JPA 리포지토리 (JPQL 필터 쿼리 포함)
├── filter/          AuditLogFilter + AuditEventListener (비동기)
├── client/          NaverGeolocationClient (WebClient, 지수 백오프)
├── service/         AuditLogService, IpGeolocationService, VisitStatsService
├── controller/      AdminController (@PreAuthorize ADMIN)
├── dto/             AuditLogResponse, VisitStatsResponse
└── util/            ClientIpExtractor, IpMasker, InfraTrafficFilter
```

**요청 흐름**: RateLimitFilter → JwtAuthenticationFilter → AuditLogFilter → Controller

AuditLogFilter는 `filterChain.doFilter()` 후 `ApplicationEventPublisher`로 비동기 이벤트를 발행한다.
`AuditEventListener`(`@Async`)가 IP 지리 조회 + audit_log 기록을 처리하므로 사용자 응답 지연이 없다.

### Calc (Python/Flask)

```
app/log/
├── model/           AuditLogEntry, StepResult, PipelineMetadata (dataclass)
├── repository/      audit_log_repository (psycopg2 직접 INSERT)
├── service/         audit_log_service (log_api, log_pipeline)
└── middleware/      audit_middleware (before/after/teardown_request)
```

- API 감사: `after_request` + `teardown_request` 병행으로 에러 시에도 기록
- 파이프라인 감사: `orchestrator._safe_step()` → `StepResult` 반환 → 종료 시 `log_pipeline()` 호출

### IP 지리 추적 흐름

1. `ClientIpExtractor`로 클라이언트 IP 추출 (X-Forwarded-For 우선)
2. `Hasher.hash(ip)`로 HMAC-SHA256 해시 생성
3. DB에서 `ip_hash`로 기존 레코드 조회
4. 없으면 `NaverGeolocationClient`로 Naver Cloud API 호출 (지수 백오프, 최대 3회)
5. 인프라 트래픽(AMAZON, GOOGLE-CLOUD 등) 필터링
6. `ip_geolocations` 테이블에 upsert

---

## 방문자 통계 (VisitStatsService)

knn.cafe의 패턴을 재활용한다.

- **중복 방문 제거**: `COUNT(DISTINCT (DATE(created_at AT TIME ZONE 'Asia/Seoul'), ip_geolocation_id))`
- **클러스터**: 국가/지역별 방문 수 + 좌표
- **시간대별**: 0~23시 KST 기준 분포
- **경로별**: 상위 20개 API 경로별 방문 수

---

## 관리자 API

| Method | Path | 설명 |
|---|---|---|
| GET | `/api/admin/logs` | 감사 로그 조회 (페이지네이션, 필터: server/action/dateRange) |
| GET | `/api/admin/visitors` | 방문자 통계 (클러스터, 시간대, 요약) |

`@PreAuthorize("hasRole('ADMIN')")` 적용. JWT의 `role` 클레임이 `ADMIN`인 사용자만 접근 가능.

---

## 파이프라인 metadata 구조

```json
{
  "command": "kr",
  "steps": [
    {"name": "fundamentals", "status": "success", "duration_ms": 12340},
    {"name": "factors", "status": "failed", "duration_ms": 5670, "error": "timeout"}
  ],
  "total_duration_ms": 120000,
  "stocks_processed": 2100,
  "coverage": {"FULL": 1800, "PARTIAL": 200}
}
```

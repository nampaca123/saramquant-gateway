# Security System

## 개요

Rate Limiting, IP Whitelist, AES-256-GCM 암호화, Blind Index, Row Level Security를 조합한 다층 보안 체계.

---

## 1. Rate Limiting (Gateway)

**위치**: `infra/security/filter/RateLimitFilter.kt`

| 항목 | 값 |
|---|---|
| 알고리즘 | Token Bucket (Bucket4j) |
| 용량(burst) | 10 tokens |
| 충전 속도 | 5 tokens/sec |
| 저장소 | ConcurrentHashMap<IP, Bucket> |
| 메모리 정리 | @Scheduled 1시간마다 미사용 버킷 제거 |
| 초과 응답 | 429 + Retry-After: 1 |

**필터 순서**: RateLimitFilter → JwtAuthenticationFilter → AuditLogFilter

인증 전에 차단하여 JWT 검증 비용을 절감한다.

---

## 2. IP Whitelist (Calc Server)

**위치**: `app/__init__.py` (Flask before_request)

- `ALLOWED_GATEWAY_IPS` 환경변수로 허용 IP 관리 (쉼표 구분)
- `/internal/*` 경로만 대상, `/health`는 제외
- IP 화이트리스트(네트워크) + API 키(애플리케이션) 이중 보안

| 환경 | 설정값 |
|---|---|
| 로컬 개발 | `127.0.0.1,::1` |
| 배포 | Gateway 서버 고정 IP |

---

## 3. 민감 데이터 암호화 (Gateway)

**위치**: `infra/security/crypto/`

### 키 파생 전략

단일 `HASH_SECRET`에서 HMAC-SHA256으로 용도별 키를 파생한다.

- **해싱 키**: `HMAC-SHA256(HASH_SECRET, "hmac-key")` → IP 해싱, email blind index
- **암호화 키**: `HMAC-SHA256(HASH_SECRET, "aes-key")` → AES-256-GCM 키

### 모듈 구성

| 파일 | 역할 |
|---|---|
| CryptoProperties | `@ConfigurationProperties`로 HASH_SECRET 바인딩 |
| Hasher | HMAC-SHA256 해싱 (IP, email blind index) |
| AesEncryptor | AES-256-GCM 양방향 암호화 (12-byte IV, Base64) |
| EncryptionConverter | JPA AttributeConverter (자동 암호화/복호화) |

### 암호화 대상

| 엔티티 | 필드 | 방식 |
|---|---|---|
| User | email | AES + blind index (email_hash) |
| User | name | AES |
| User | providerId | AES |
| UserProfile | nickname | AES |

이미 보호된 필드 (변경 없음): `User.passwordHash` (BCrypt), `RefreshToken.tokenHash`

### Blind Index

email 조회 시 `Hasher.hash(email)` → `UserRepository.findByEmailHash()` 사용.
AES 암호문은 매번 다른 IV로 생성되어 직접 검색 불가하므로, HMAC 해시를 별도 컬럼에 저장하여 조회한다.

### 키 로테이션 시나리오

- **AES 암호화 데이터**: 이전 키로 복호화 → 새 키로 재암호화 (마이그레이션 가능)
- **email blind index**: AES로 복호화 → 새 키로 재해싱 (마이그레이션 가능)
- **ip_geolocations.ip_hash**: 원본 IP 미저장으로 복구 불가 → 재수집으로 대응 (수용 가능)

---

## 4. Row Level Security (Supabase)

### 적용 대상

전체 25개 public 테이블에 RLS 활성화:
- 기존 23개: users, user_profiles, user_preferred_markets, refresh_tokens, ai_usage_logs, stock_ai_analyses, stocks, daily_prices, financial_statements, stock_fundamentals, stock_indicators, factor_exposures, sector_aggregates, risk_badges, exchange_rates, risk_free_rates, benchmark_daily_prices, user_portfolios, portfolio_holdings, factor_covariance, factor_returns, ml_models, predictions
- 신규 2개: ip_geolocations, audit_log

### 작동 원리

앱은 `postgres` 역할(테이블 소유자)로 접속하므로 RLS를 바이패스한다.
별도 policy 없이 RLS만 활성화하면, Supabase REST API/Client SDK를 통한 `anon`/`authenticated` 역할의 비인가 접근이 전면 차단된다.

향후 프론트엔드에서 Supabase Client SDK를 직접 사용할 경우, 해당 테이블에 적절한 policy 추가가 필요하다.

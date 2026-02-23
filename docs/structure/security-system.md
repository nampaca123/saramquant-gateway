# Security System

## 개요

Rate Limiting, IP Whitelist, AES-256-GCM 암호화, Blind Index, Row Level Security, SQL Injection 방어를 조합한 다층 보안 체계.

> SQL Injection 방어에 대한 상세 설명은 섹션 5 참조.

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

---

## 5. SQL Injection 방어 (학습용)

### SQL Injection이란?

사용자 입력이 SQL 쿼리 문자열에 **그대로 삽입**될 때, 공격자가 입력값에 SQL 구문을 끼워넣어 의도하지 않은 쿼리를 실행시키는 공격.

```
-- 원래 의도된 쿼리
SELECT * FROM users WHERE email = '사용자입력'

-- 공격자가 입력란에 ' OR '1'='1 을 넣으면
SELECT * FROM users WHERE email = '' OR '1'='1'
→ WHERE 조건이 항상 참 → 전체 유저 데이터 노출
```

### 왜 "정규식 필터"로는 못 막는가?

앱 레벨에서 정규식으로 `SELECT`, `UNION`, `--` 같은 패턴을 차단하는 접근이 자주 시도되지만, 프로덕션에서는 비추천이다.

**우회가 너무 쉽다:**
```
UN/**/ION SEL/**/ECT   -- 블록 주석으로 키워드 쪼개기
%27%20OR%201%3D1       -- URL 인코딩
0x554e494f4e           -- 16진수 인코딩
```

**오탐이 많다:**
- `O'Reilly and Sons` → `'\s*(OR|AND)` 패턴에 걸림
- `가격 -- 설명` → `--\s` 패턴에 걸림
- 검색/메모/필터 입력에서 정상 요청이 차단됨

**검사 범위도 불완전하다:**
- `parameterMap`은 JSON body(`@RequestBody`)를 포함하지 않음
- Header, Cookie, PathVariable도 검사 불가
- 결국 "특정 문자열을 막는 필터"이지, "SQL Injection을 막는 필터"가 아님

### 올바른 방어 전략

SQL Injection은 **쿼리를 만드는 시점**에서 원천 차단해야 한다. 입력을 검열하는 것이 아니라, 입력이 무엇이든 SQL 구문으로 해석될 수 없는 구조를 만드는 것이 핵심이다.

#### 1. Parameterized Query (근본 방어)

사용자 입력을 SQL 문자열에 직접 넣지 않고, **플레이스홀더(:param)** 로 분리하여 DB 드라이버가 바인딩한다.
DB는 플레이스홀더 자리의 값을 **"데이터"로만 처리**하므로, 어떤 값이 들어와도 SQL 구문으로 실행되지 않는다.

```kotlin
// ❌ 위험: 문자열 보간 → 값이 SQL 구문의 일부가 됨
val sql = "SELECT * FROM users WHERE email = '$email'"
em.createNativeQuery(sql)

// ✅ 안전: 파라미터 바인딩 → 값이 데이터로만 처리됨
val sql = "SELECT * FROM users WHERE email = :email"
em.createNativeQuery(sql).setParameter("email", email)
```

이 프로젝트에서의 적용:

| 방식 | 파일 예시 | 동작 원리 |
|---|---|---|
| JPA `@Query` + `:param` | StockRepository, DailyPriceRepository | Spring Data가 PreparedStatement로 자동 변환 |
| EntityManager + `setParameter()` | DashboardQueryRepository, VisitStatsService | 네이티브 쿼리에서 수동 바인딩 |

#### 2. 식별자 화이트리스트 (ORDER BY / 컬럼명)

파라미터 바인딩은 **값(WHERE 조건)** 에만 적용된다.
`ORDER BY`, 테이블명, 컬럼명 같은 **식별자**는 바인딩이 불가능하므로, 사용자 입력을 직접 넣으면 위험하다.

```kotlin
// ❌ 위험: 사용자가 sort에 "name; DROP TABLE users" 를 넣을 수 있음
val sql = "SELECT * FROM stocks ORDER BY $sort"

// ✅ 안전: 허용된 값만 매핑, 나머지는 기본값
val SORT_MAP = mapOf(
    "market_cap_desc" to "s.market_cap DESC",
    "market_cap_asc"  to "s.market_cap ASC",
    "name_asc"        to "s.name ASC",
)
val orderBy = SORT_MAP[sort] ?: "s.market_cap DESC"
val sql = "SELECT * FROM stocks ORDER BY $orderBy"
```

이 프로젝트에서의 적용: `DashboardQueryRepository`의 `SORT_MAP`

#### 3. Bean Validation (입력 스키마 검증)

"위험한 패턴"을 블랙리스트로 거르는 것이 아니라, "허용된 형식"만 화이트리스트로 통과시킨다.

```kotlin
// "SQL 키워드가 있는지" 검사 (블랙리스트) → 오탐 많고 우회 쉬움
// "이메일 형식인지" 검사 (화이트리스트) → 정확하고 우회 불가

data class SignupRequest(
    @field:Email val email: String,
    @field:Size(min = 8, max = 100) val password: String,
    @field:Size(min = 1, max = 50) val name: String,
)
```

이 프로젝트에서의 적용: `ManualSignupRequest`, `ManualLoginRequest`에 `@Email`, `@Size` 적용.

#### 4. WAF (네트워크 레벨 탐지)

패턴 기반 탐지가 필요하다면, 앱 내부 필터보다 **WAF(Web Application Firewall)** 가 적합하다.
Cloudflare WAF, AWS WAF 등은 수천 개의 룰셋과 인코딩 디코딩 처리가 내장되어 있어 앱 레벨 정규식보다 훨씬 정교하다.

### 방어 계층 요약

```
[WAF]                    패턴 기반 탐지 (인코딩/우회 대응 내장)
  ↓
[Bean Validation]        입력 형식 화이트리스트 (@Email, @Size, @Pattern)
  ↓
[Parameterized Query]    값이 SQL 구문으로 해석되는 것 자체를 차단
[식별자 화이트리스트]       ORDER BY 등 바인딩 불가 영역은 허용값만 매핑
```

Parameterized Query + 식별자 화이트리스트가 **근본 방어**이고, 나머지는 보조 계층이다.

### 코드 리뷰 체크리스트

새로운 쿼리를 작성할 때 아래만 확인하면 SQL Injection은 원천 차단된다:

- [ ] 문자열 보간(`"$변수"`, `'${변수}'`)이 SQL에 직접 들어가는 곳이 없는가?
- [ ] 모든 WHERE 조건 값은 `setParameter()` 또는 JPA `:param`으로 바인딩하는가?
- [ ] ORDER BY / 컬럼명에 사용자 입력을 쓴다면, 화이트리스트 맵을 거치는가?

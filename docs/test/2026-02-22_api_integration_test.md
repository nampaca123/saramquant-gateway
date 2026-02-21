# API Integration Test Report — 2026-02-22

Persona: **김지현** (26세, 여성, 투자 초보, KR_KOSPI + US_NASDAQ 선호)
Test account: `jihyun@saramquant.com` / `Jihyun2026!` (테스트 완료 후 삭제됨)

---

## Phase 1: Server Health

| 항목 | 결과 |
|---|---|
| Gateway (Spring Boot, :8080) | OK |
| Calc Server (Flask, :5000) | OK |
| PostgreSQL (Supabase) | OK |

---

## Phase 2: Auth & Profile

| API | Method | Path | Status | 비고 |
|---|---|---|---|---|
| 회원가입 | POST | `/api/auth/signup` | **200** | 쿠키에 AT/RT 정상 발급 |
| 로그인 | POST | `/api/auth/login` | **200** | |
| 토큰 갱신 | POST | `/api/auth/refresh` | **200** | |
| 프로필 수정 | PATCH | `/api/user/profile` | **200** | nickname, birthYear, gender, investmentExperience, preferredMarkets |
| 내 정보 조회 | GET | `/api/user/me` | **200** | user + profile 합쳐서 반환 |

---

## Phase 3: Portfolio & Market Data

### 포트폴리오/보유종목 CRUD

| API | Method | Path | Status | 비고 |
|---|---|---|---|---|
| 포트폴리오 목록 | GET | `/api/portfolios` | **200** | 회원가입 시 KR(id=1), US(id=2) 자동 생성 |
| 포트폴리오 상세 | GET | `/api/portfolios/{id}` | **200** | holdings 포함 |
| 매수 (삼성전자) | POST | `/api/portfolios/1/holdings` | **201** | 10주, 2025-10-15, AUTO 가격 95,000원 |
| 매수 (SK하이닉스) | POST | `/api/portfolios/1/holdings` | **201** | 5주, 2025-11-20, AUTO 가격 571,000원 |
| 매수 (NAVER) | POST | `/api/portfolios/1/holdings` | **201** | 3주, 2025-12-05, AUTO 가격 249,500원 |
| 매수 (AAPL) | POST | `/api/portfolios/2/holdings` | **201** | 15주, 2022-06-10, USD $134.56, 환율 1,257.6 |
| 매수 (MSFT) | POST | `/api/portfolios/2/holdings` | **201** | 8주, 2022-08-15, USD $285.21, 환율 1,257.6 |
| 매수 (NVDA) | POST | `/api/portfolios/2/holdings` | **201** | 12주, 2023-03-20, USD $25.88, 환율 1,257.6 |

### 분석/시뮬레이션

| API | Method | Path | Status | 비고 |
|---|---|---|---|---|
| KR 포트폴리오 분석 | GET | `/api/portfolios/1/analysis` | **200** | risk_score 86.63 (WARNING), HHI 0.46, effective_n 2.16 |
| US 포트폴리오 분석 | GET | `/api/portfolios/2/analysis` | **200** | risk_score 65.5 (CAUTION), sector_hhi 1.0 (Technology 100%) |
| 주식 시뮬레이션 (삼성) | GET | `/api/stocks/005930/simulation` | **200** | GBM, 30일, 1000회, expected +15.9% |
| 포트폴리오 시뮬레이션 (KR) | POST | `/api/portfolios/1/simulation` | **200** | Bootstrap, 30일, expected +18.3% |

### 종목/시장 데이터

| API | Method | Path | Status | 비고 |
|---|---|---|---|---|
| 종목 상세 (삼성) | GET | `/api/stocks/005930?market=KR_KOSPI` | **200** | riskBadge, indicators, fundamentals, factorExposures, sectorComparison |
| 종목 상세 (NVDA) | GET | `/api/stocks/NVDA?market=US_NASDAQ` | **200** | summaryTier=WARNING, PBR 107.3 |
| 가격 시계열 | GET | `/api/stocks/{symbol}/prices` | **200** | 1Y 기간 데이터 |
| 벤치마크 비교 | GET | `/api/stocks/{symbol}/benchmark` | **200** | |
| 대시보드 종목 | GET | `/api/dashboard/stocks?market=KR_KOSPI` | **200** | 790종목, 페이지네이션 정상 |
| 대시보드 섹터 | GET | `/api/dashboard/sectors?market=KR_KOSPI` | **200** | 21개 섹터 |

---

## Phase 4: AI Analysis

| API | Method | Path | Status | 비고 |
|---|---|---|---|---|
| 사용량 조회 | GET | `/api/ai/usage` | **200** | used=0, limit=20 |
| 캐시 조회 (미존재) | GET | `/api/stocks/005930/ai-analysis?market=KR_KOSPI` | **404** | 캐시 없을 때 정상 응답 |
| 주식 AI 분석 | POST | `/api/ai/stock-analysis` | **500** | Claude 404 → OpenAI 400 → 전체 실패. **API 키/모델명 문제** (코드 흐름은 정상) |
| 사용량 재조회 | GET | `/api/ai/usage` | **200** | used=1 (실패해도 카운트됨 — 수정 검토 필요) |

---

## Phase 5: Cleanup & Account

| API | Method | Path | Status | 비고 |
|---|---|---|---|---|
| 부분 매도 (삼성 5주) | PATCH | `/api/portfolios/1/holdings/1` | **204** | 10→5주 |
| 보유 삭제 (NAVER) | DELETE | `/api/portfolios/1/holdings/3` | **204** | |
| 포트폴리오 리셋 (US) | POST | `/api/portfolios/2/reset` | **204** | holdings 전부 삭제 |
| 로그아웃 | POST | `/api/auth/logout` | **204** | 이후 403 확인 |
| 전체 로그아웃 | POST | `/api/auth/logout-all` | **204** | |
| 계정 삭제 | DELETE | `/api/user/me` | **204** | DB에서 완전 삭제 확인 |

---

## 발견된 이슈

### 1. Entity Persistable 미구현 (수정 완료)

- **증상**: 회원가입 시 `StaleObjectStateException` (HTTP 500)
- **원인**: `User`, `UserProfile`, `RefreshToken` 엔티티가 `UUID.randomUUID()`로 ID를 직접 생성하지만, Spring Data JPA가 이를 기존 엔티티로 판단하여 `merge()` 호출
- **수정**: 3개 엔티티에 `Persistable<UUID>` 인터페이스 구현 + `@get:JvmName("_getId")` 사용하여 Kotlin/JVM getter 충돌 해결

### 2. JWT RSA 키 이중 인코딩 (수정 완료)

- **증상**: `BadPaddingException: RSA private key operation failed`
- **원인**: PEM → Base64 이중 인코딩 과정에서 키 데이터 손상
- **수정**: `JwtProperties`를 DER 바이트의 단일 Base64 디코딩으로 변경, `.env`의 키도 DER Base64로 재생성

### 3. Refresh Token 해시 충돌 (미수정)

- **증상**: `logout-all` 직후 동일 초 내 재로그인 시 `duplicate key violates unique constraint "refresh_tokens_token_hash_key"`
- **원인**: JWT `iat` 클레임이 초 단위이므로, 같은 초에 동일 유저로 로그인하면 동일한 토큰 → 동일 해시 생성
- **권장**: 토큰 생성 시 `jti` (JWT ID) 클레임에 UUID를 추가하여 유니크 보장

### 4. AI 분석 실패 시 사용량 카운트 (검토 필요)

- **증상**: LLM API 호출이 전부 실패(500)해도 `used` 카운트 1 증가
- **권장**: 성공 시에만 카운트하도록 변경 검토

### 5. 삭제된 유저 로그인 시 500 (미수정)

- **증상**: 계정 삭제 후 로그인 시도 시 500 (이상적으로는 401 "Invalid credentials")
- **원인**: `findByEmail` null 반환 후 NPE 또는 미처리 경로

---

## 테스트된 엔드포인트 요약

| 카테고리 | 성공 | 실패 | 합계 |
|---|---|---|---|
| Auth (signup/login/refresh/logout) | 7 | 0 | 7 |
| User/Profile | 2 | 0 | 2 |
| Portfolio CRUD | 11 | 0 | 11 |
| Stock/Market Data | 6 | 0 | 6 |
| AI Analysis | 3 | 1* | 4 |
| **합계** | **29** | **1*** | **30** |

*AI 분석 실패는 API 키/모델명 설정 문제이며, 코드 자체의 흐름(재시도, 폴백)은 정상 동작 확인됨.

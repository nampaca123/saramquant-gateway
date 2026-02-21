# SaramQuant Gateway Server - Directory Structure

## 아키텍처 개요

Spring Boot (Kotlin) 기반 BFF(Backend for Frontend). 클라이언트 요청을 받아 인증, 캐싱, 데이터 집계를 처리하고, 무거운 연산은 Calc Server에 위임한다.

```
saramquant-gateway/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew / gradlew.bat
├── Dockerfile
│
└── src/main/
    ├── kotlin/me/saramquantgateway/
    │   ├── SaramquantGatewayApplication.kt
    │   │
    │   ├── domain/                         # 순수 데이터 모델 (외부 의존 없음)
    │   │   ├── converter/
    │   │   │   └── market/
    │   │   │       └── MaturityConverter.kt
    │   │   ├── entity/
    │   │   │   ├── llm/
    │   │   │   │   ├── LlmUsageLog.kt       # 사용자별 일일 LLM 호출 횟수 기록
    │   │   │   │   └── StockLlmAnalysis.kt  # 종목 LLM 분석 결과 캐시
    │   │   │   ├── auth/
    │   │   │   │   └── RefreshToken.kt     # Refresh Token (UUID, 만료일, 사용자 매핑)
    │   │   │   ├── factor/
    │   │   │   │   └── FactorExposure.kt   # 종목별 팩터 노출도 (size_z, value_z 등)
    │   │   │   ├── fundamental/
    │   │   │   │   ├── FinancialStatement.kt  # 재무제표 (매출, 영업이익 등)
    │   │   │   │   └── StockFundamental.kt    # 계산된 펀더멘털 지표 (PER, PBR, ROE 등)
    │   │   │   ├── indicator/
    │   │   │   │   └── StockIndicator.kt   # 기술적 지표 (SMA, RSI, MACD 등 + 베타/샤프)
    │   │   │   ├── market/
    │   │   │   │   ├── BenchmarkDailyPrice.kt # 벤치마크 지수 일봉 (KOSPI, S&P500 등)
    │   │   │   │   ├── ExchangeRate.kt        # 환율 (KRW/USD 등)
    │   │   │   │   ├── RiskFreeRate.kt        # 무위험이자율 (KR/US, 만기별)
    │   │   │   │   └── SectorAggregate.kt     # 섹터별 중위수 PER, PBR, ROE 등
    │   │   │   ├── portfolio/
    │   │   │   │   ├── PortfolioHolding.kt    # 포트폴리오 보유 종목 (매수가, 수량, 환율)
    │   │   │   │   └── UserPortfolio.kt       # 포트폴리오 헤더 (이름, 소유자)
    │   │   │   ├── riskbadge/
    │   │   │   │   └── RiskBadge.kt           # 리스크뱃지 (summary_tier, dimensions JSONB)
    │   │   │   ├── stock/
    │   │   │   │   ├── DailyPrice.kt          # 종목 일봉 OHLCV
    │   │   │   │   ├── Stock.kt               # 종목 마스터 (symbol, market, sector, is_active)
    │   │   │   │   └── StockDateId.kt         # 복합 PK용 Embeddable (stock_id + date)
    │   │   │   └── user/
    │   │   │       ├── User.kt                # 사용자 계정 (이메일, 비밀번호해시, OAuth provider)
    │   │   │       └── UserProfile.kt         # 사용자 프로필 (닉네임, 투자경험, 프로필이미지)
    │   │   │
    │   │   ├── enum/
    │   │   │   ├── auth/
    │   │   │   │   └── AuthProvider.kt        # GOOGLE, KAKAO, MANUAL
    │   │   │   ├── fundamental/
    │   │   │   │   └── ReportType.kt          # ANNUAL, Q1, Q2, Q3
    │   │   │   ├── market/
    │   │   │   │   ├── Benchmark.kt           # KR_KOSPI, KR_KOSDAQ, US_SP500, US_NASDAQ
    │   │   │   │   ├── Country.kt             # KR, US
    │   │   │   │   └── Maturity.kt            # 91D, 1Y, 3Y, 10Y
    │   │   │   ├── portfolio/
    │   │   │   │   └── MarketGroup.kt         # KR, US (포트폴리오 시장 구분)
    │   │   │   ├── stock/
    │   │   │   │   ├── Direction.kt           # UP, DOWN
    │   │   │   │   ├── Market.kt              # KR_KOSPI, KR_KOSDAQ, US_NYSE, US_NASDAQ
    │   │   │   │   └── PricePeriod.kt         # 1M, 3M, 6M, 1Y 등
    │   │   │   └── user/
    │   │   │       ├── Gender.kt              # MALE, FEMALE, UNSPECIFIED
    │   │   │       ├── InvestmentExperience.kt # BEGINNER, INTERMEDIATE, ADVANCED
    │   │   │       └── UserRole.kt            # STANDARD, ADMIN
    │   │   │
    │   │   └── repository/                    # JpaRepository 인터페이스 모음
    │   │       ├── llm/
    │   │       │   ├── LlmUsageLogRepository.kt
    │   │       │   └── StockLlmAnalysisRepository.kt
    │   │       ├── auth/
    │   │       │   └── RefreshTokenRepository.kt
    │   │       ├── factor/
    │   │       │   └── FactorExposureRepository.kt
    │   │       ├── fundamental/
    │   │       │   ├── FinancialStatementRepository.kt
    │   │       │   └── StockFundamentalRepository.kt
    │   │       ├── indicator/
    │   │       │   └── StockIndicatorRepository.kt
    │   │       ├── market/
    │   │       │   ├── BenchmarkDailyPriceRepository.kt
    │   │       │   ├── ExchangeRateRepository.kt
    │   │       │   ├── RiskFreeRateRepository.kt
    │   │       │   └── SectorAggregateRepository.kt
    │   │       ├── portfolio/
    │   │       │   ├── PortfolioHoldingRepository.kt
    │   │       │   └── UserPortfolioRepository.kt
    │   │       ├── riskbadge/
    │   │       │   └── RiskBadgeRepository.kt
    │   │       ├── stock/
    │   │       │   ├── DailyPriceRepository.kt
    │   │       │   └── StockRepository.kt
    │   │       └── user/
    │   │           ├── UserProfileRepository.kt
    │   │           └── UserRepository.kt
    │   │
    │   ├── feature/                           # 기능 단위 비즈니스 로직
    │   │   ├── llm/
    │   │   │   ├── controller/
    │   │   │   │   └── LlmAnalysisController.kt  # LLM 분석 트리거, 캐시 조회, 사용량 조회
    │   │   │   ├── service/
    │   │   │   │   ├── LlmCacheCleanupScheduler.kt  # 30일 이상 캐시 정기 삭제
    │   │   │   │   ├── LlmUsageService.kt           # 일별 LLM 호출 횟수 관리 (원자적 증가)
    │   │   │   │   ├── PortfolioLlmService.kt       # 포트폴리오 LLM 분석
    │   │   │   │   ├── PromptBuilder.kt             # KO/EN 프롬프트 빌더
    │   │   │   │   └── StockLlmService.kt           # 종목 LLM 분석 (thundering herd 방지 캐싱)
    │   │   │   └── dto/
    │   │   │       └── LlmDtos.kt
    │   │   │
    │   │   ├── dashboard/
    │   │   │   ├── controller/
    │   │   │   │   └── DashboardController.kt      # 종목 카드 목록 (market, tier, sector 필터 + 페이지네이션)
    │   │   │   ├── service/
    │   │   │   │   └── DashboardService.kt         # 배치 조회로 N+1 방지
    │   │   │   └── dto/
    │   │   │       └── DashboardDtos.kt
    │   │   │
    │   │   ├── portfolio/
    │   │   │   ├── controller/
    │   │   │   │   └── PortfolioController.kt      # 포트폴리오 CRUD, 매수/매도
    │   │   │   ├── service/
    │   │   │   │   └── PortfolioService.kt         # 평균단가 계산, 환율 처리 포함
    │   │   │   └── dto/
    │   │   │       ├── HoldingRequest.kt
    │   │   │       └── PortfolioResponse.kt
    │   │   │
    │   │   ├── simulation/
    │   │   │   ├── controller/
    │   │   │   │   └── SimulationController.kt     # Calc Server 시뮬레이션 프록시
    │   │   │   ├── service/
    │   │   │   │   └── SimulationService.kt
    │   │   │   └── dto/
    │   │   │       └── SimulationDtos.kt
    │   │   │
    │   │   └── stock/
    │   │       ├── controller/
    │   │       │   └── StockController.kt          # 종목 상세, OHLCV, 벤치마크 비교
    │   │       ├── service/
    │   │       │   └── StockService.kt             # 지표/펀더멘털/리스크뱃지 집계
    │   │       └── dto/
    │   │           └── StockDetailDtos.kt
    │   │
    │   └── infra/                             # 외부 시스템 연동
    │       ├── llm/
    │       │   ├── config/
    │       │   │   └── LlmProperties.kt            # LLM 모델명, API키, daily-limit 설정
    │       │   └── lib/
    │       │       ├── AnthropicClient.kt          # Claude API 클라이언트
    │       │       ├── LlmClient.kt               # LLM 공통 인터페이스
    │       │       ├── LlmRouter.kt               # 재시도 + fallback 라우팅
    │       │       └── OpenAiClient.kt            # OpenAI API 클라이언트
    │       │
    │       ├── auth/
    │       │   ├── controller/
    │       │   │   └── AuthController.kt          # OAuth 콜백, 회원가입/로그인, 토큰 갱신/로그아웃
    │       │   ├── service/
    │       │   │   └── AuthService.kt
    │       │   └── dto/
    │       │       ├── ManualLoginRequest.kt
    │       │       └── ManualSignupRequest.kt
    │       │
    │       ├── connection/
    │       │   ├── CalcServerClient.kt            # Calc Server HTTP 클라이언트 (GET/POST)
    │       │   └── CalcServerProperties.kt        # url, auth-key, timeoutMs
    │       │
    │       ├── jwt/
    │       │   ├── lib/
    │       │   │   ├── JwtProperties.kt           # RSA 키페어 (Base64), TTL 설정
    │       │   │   └── JwtProvider.kt             # Access/Refresh Token 발급·검증
    │       │   └── service/
    │       │       └── RefreshTokenService.kt     # 토큰 rotation, 전체 세션 revoke
    │       │
    │       ├── oauth/
    │       │   ├── lib/
    │       │   │   ├── GoogleOAuthClient.kt
    │       │   │   ├── KakaoOAuthClient.kt
    │       │   │   ├── OAuthClient.kt             # OAuth 공통 인터페이스
    │       │   │   └── OAuthProperties.kt         # client-id, secret, redirect-uri
    │       │   └── dto/
    │       │       ├── OAuthTokenResponse.kt
    │       │       └── OAuthUserInfo.kt
    │       │
    │       ├── log/                               # 감사 로그 + 방문자 추적
    │       │   ├── entity/
    │       │   │   ├── AuditLog.kt               # audit_log 테이블 매핑
    │       │   │   └── IpGeolocation.kt          # ip_geolocations 테이블 매핑
    │       │   ├── repository/
    │       │   │   ├── AuditLogRepository.kt     # 필터 쿼리 + 페이지네이션
    │       │   │   └── IpGeolocationRepository.kt
    │       │   ├── filter/
    │       │   │   └── AuditLogFilter.kt         # 비동기 이벤트 발행 (AuditEventListener 포함)
    │       │   ├── client/
    │       │   │   └── NaverGeolocationClient.kt # Naver Cloud API (지수 백오프, 3회 재시도)
    │       │   ├── service/
    │       │   │   ├── AuditLogService.kt        # 관리자 로그 조회
    │       │   │   ├── IpGeolocationService.kt   # IP→지리 정보 (DB→Naver API→upsert)
    │       │   │   └── VisitStatsService.kt      # 방문자 통계 (클러스터, 시간대, 경로별)
    │       │   ├── controller/
    │       │   │   └── AdminController.kt        # GET /api/admin/logs, /visitors (ADMIN only)
    │       │   ├── dto/
    │       │   │   ├── AuditLogResponse.kt
    │       │   │   └── VisitStatsResponse.kt
    │       │   └── util/
    │       │       ├── ClientIpExtractor.kt      # X-Forwarded-For 등에서 클라이언트 IP 추출
    │       │       ├── IpMasker.kt               # IP 마스킹 (X.X.*.*)
    │       │       └── InfraTrafficFilter.kt     # 인프라 트래픽(봇) 필터링
    │       │
    │       ├── security/
    │       │   ├── config/
    │       │   │   └── SecurityConfig.kt          # Spring Security 설정, CORS, 필터 체인 등록
    │       │   ├── crypto/
    │       │   │   ├── CryptoProperties.kt        # HASH_SECRET 바인딩
    │       │   │   ├── Hasher.kt                  # HMAC-SHA256 (IP 해싱, email blind index)
    │       │   │   ├── AesEncryptor.kt            # AES-256-GCM 양방향 암호화
    │       │   │   └── EncryptionConverter.kt     # JPA AttributeConverter (자동 암/복호화)
    │       │   ├── CookieUtil.kt                  # HttpOnly 쿠키 생성/삭제 유틸
    │       │   └── filter/
    │       │       ├── JwtAuthenticationFilter.kt # 쿠키에서 JWT 추출 → SecurityContext 설정
    │       │       └── RateLimitFilter.kt         # Bucket4j IP당 5req/sec, burst 10
    │       │
    │       ├── storage/
    │       │   ├── lib/
    │       │   │   ├── SupabaseStorageClient.kt       # Supabase Storage 업로드/삭제
    │       │   │   └── SupabaseStorageProperties.kt   # url, bucket, secret-key
    │       │   └── service/
    │       │       └── ProfileImageService.kt     # 프로필 이미지 업로드 처리
    │       │
    │       └── user/
    │           ├── controller/
    │           │   └── UserController.kt          # GET/PATCH /api/users/me
    │           ├── service/
    │           │   ├── ProfileService.kt
    │           │   └── UserService.kt
    │           └── dto/
    │               ├── ProfileResponse.kt
    │               ├── ProfileUpdateRequest.kt
    │               └── UserResponse.kt
    │
    └── resources/
        └── application.properties
```

---

## 레이어 구조 설명

### `domain/`
JPA Entity, Enum, Repository 인터페이스만 포함한다. Spring 외 의존성을 갖지 않으며, entity 하위 패키지는 DB 테이블을 1:1로 반영한다.

> entity와 repository 패키지 구조를 동일하게 맞추어, 어느 Entity의 Repository인지 즉시 파악할 수 있다.

### `feature/`
기능 단위로 묶인 비즈니스 로직. 각 기능은 `controller / service / dto` 세 디렉터리를 가진다.

| 기능 | 설명 |
|------|------|
| `llm` | LLM 종목·포트폴리오 분석 (캐싱, rate limiting, thundering herd 방지) |
| `dashboard` | 종목 스크리너 목록 (N+1 배치 조회) |
| `portfolio` | 포트폴리오 CRUD + 매수/매도 (평균단가, 환율 처리) |
| `simulation` | 몬테카를로 시뮬레이션 (Calc Server 프록시) |
| `stock` | 종목 상세 (지표, 펀더멘털, 리스크뱃지, LLM 분석 집계) |

### `infra/`
외부 시스템 연동 및 기반 설정. 각 하위 패키지는 하나의 외부 관심사를 담당한다.

| 패키지 | 담당 |
|--------|------|
| `llm` | Claude / OpenAI LLM 클라이언트, fallback 라우터 |
| `auth` | 회원가입 / 로그인 / 토큰 관리 API |
| `connection` | Calc Server HTTP 클라이언트 |
| `jwt` | RSA 키페어 기반 JWT 발급·검증, Refresh Token rotation |
| `log` | 감사 로그, IP 지리 추적 (Naver Cloud), 방문자 통계, 관리자 API |
| `oauth` | Google / Kakao OAuth 코드 교환 |
| `security` | Spring Security 설정, JWT 쿠키 필터, Rate Limiting (Bucket4j), AES-256-GCM 암호화 |
| `storage` | Supabase Storage 파일 업로드 |
| `user` | 사용자 프로필 조회·수정 API |

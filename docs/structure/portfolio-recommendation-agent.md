# 포트폴리오 추천 AI Agent 설계 문서

> Claude Tool Use를 활용한 자율형 AI Agent가 사용자의 포트폴리오를 분석하고 개선 방안을 추천한다.

---

## 1. 개요

### 1-1. 목적

기존 LLM 통합이 **해석**(이 종목의 리스크 특성은 ~)이라면, 추천 Agent는 **의사결정 보조**(기술주 편중이니 ~를 추가하면 분산 효과가 높아집니다)이다.

Agent는 사전 주입된 포트폴리오 분석 데이터를 바탕으로 판단하고, `find_candidates`로 후보 종목을 검색한 뒤, `validate_portfolio`로 리스크를 검증하여 추천을 생성한다.

### 1-2. 기존 LLM 기능과의 차이

| 구분 | 종목/포트폴리오 분석 | 포트폴리오 추천 Agent |
|------|---------------------|---------------------|
| 패턴 | 단일 LLM 호출 | Agent Loop (다중 호출 + 도구 실행) |
| 모델 | Sonnet / Opus | Opus (복합 추론) |
| 입력 | 서버가 조립한 컨텍스트 | 사전 주입 컨텍스트 + Agent가 도구로 수집 |
| 출력 | 자연어 분석 리포트 | 구조화된 JSON (종목 + 비중 + 근거) |
| 비용 | 1 크레딧 | 3 크레딧 |
| 응답 방식 | 동기 HTTP | SSE 스트리밍 |

---

## 2. 아키텍처

### 2-1. 요청 흐름

```
[Frontend 포트폴리오 페이지]
    ↓ GET /api/llm/portfolio-recommendation?marketGroup=KR&lang=ko&direction=IMPROVE (SSE)

RecommendationController
    ├── 입력 검증 (marketGroup: KR|US, direction: IMPROVE|CONSERVATIVE|GROWTH)
    ├── checkAndIncrementBy(userId, 3) — 원자적 크레딧 차감
    ├── PortfolioService.getPortfolioDetail() → 기존 보유 종목 로드
    ├── UserProfile 로드
    └── llmExecutor.execute() → 비동기 Agent 실행
         └── 실패 시 decrementBy(userId, 3) — 크레딧 자동 복원

RecommendationAgentService (Agent Loop)
    ├── 0. toolExecutor.direction 설정
    ├── 1. RecommendationContextBuilder.build() — 사전 계산
    │       ├── 보유 종목 indicators/fundamentals/factorExposure 배치 조회
    │       ├── 포트폴리오 팩터 노출 + 변동성 + HHI 계산
    │       └── 섹터 개요 (marketGroup 기준)
    ├── 2. userMessage 구성 (사전 계산 결과 + 프로필 + direction 포함)
    ├── 3. Claude API 스트리밍 호출 (tools 포함, 재시도 2회)
    ├── 4. Claude가 tool_use → ToolExecutor 병렬 실행
    ├── 5. tool_result를 truncate 후 Claude에 반환
    ├── 6. 3~5 반복 (최대 4회, 매 iteration disconnect 체크)
    ├── 7. 최종 JSON 파싱 + DB 저장 + SSE result 이벤트
    └── 매 단계 SSE progress/thinking/tool_call/tool_result 이벤트

    ↓ SSE events
[Frontend] ← progress: "포트폴리오를 분석하고 있습니다..."
[Frontend] ← progress: "후보 종목을 검색하고 있습니다..."
[Frontend] ← result: { stocks: [...], overall_reasoning: "..." }
```

### 2-2. 설계 철학: Pre-injection + Precise Query

Agent가 **탐색하지 않고 판단만** 하도록 설계한다.

- **사전 주입(Pre-injection)**: 기존 보유 종목의 상세 메트릭, 포트폴리오 리스크 평가, 섹터 개요를 에이전트 시작 전에 서버측에서 계산하여 `userMessage`에 포함. 기존 `get_stock_detail`, `get_sector_overview`, `evaluate_portfolio` 3개 도구 호출을 제거.
- **Direction 프리셋**: 수치 필터(beta, sharpe, ROE 등)를 AI에게 노출하지 않고, `direction`(CONSERVATIVE/GROWTH/IMPROVE)에 따라 서버가 자동 적용. AI는 섹터/정렬/갯수만 결정.
- **Enriched 결과**: `find_candidates`가 스크리닝 + 팩터 노출 + 섹터 대비 밸류에이션을 한 번에 반환. 후속 조회 불필요.

### 2-3. 목표 워크플로우 (2-3 이터레이션)

```
사전 계산 (~1s)
  → 보유 종목 메트릭 + 리스크 평가 + 섹터 개요를 userMessage에 포함

이터레이션 1: find_candidates(조건1) + find_candidates(조건2) 병렬
  → direction 프리셋 적용 + enrichment 포함된 후보 반환

이터레이션 2: validate_portfolio(최종 구성) + JSON 출력
  → 또는 warnings 시 조정 후 이터레이션 3에서 출력
```

---

## 3. 도구 설계 (3개)

### 3-1. 설계 원칙

- **AI 결정 공간 최소화**: 수치 필터는 서버가 direction 기반으로 자동 적용. AI는 "무엇을 찾을지"만 결정.
- **One-shot enrichment**: 스크리닝 결과에 팩터 노출 + 섹터 비교를 자동 포함. 후속 상세 조회 불필요.
- **검증 필수**: `validate_portfolio`로 최종 추천 전 리스크를 정량 검증.
- **처방적 2단계**: 시스템 프롬프트에서 find_candidates → validate_portfolio → JSON 출력 순서를 명시.

### 3-2. 도구 목록

| 도구 | 역할 | AI가 제공하는 입력 | 서버가 자동 적용 | 출력 |
|------|------|-------------------|----------------|------|
| `find_candidates` | 후보 종목 검색 + enrichment | market(KR/US), sectors, exclude_stock_ids, sort, limit | direction별 beta/sharpe/roe/debtRatio/tier 프리셋 | 종목별 메트릭 + factorExposure + vsSector |
| `validate_portfolio` | 포트폴리오 리스크 검증 | stocks [{stock_id, weight}] | — | 팩터 노출, 추정 변동성, HHI, 가중평균 지표, 경고 |
| `web_search` (빌트인) | 최신 시장 동향/뉴스 | Claude가 자율 호출 | maxUses=2 | Anthropic 서버 자동 처리 |

### 3-3. find_candidates — direction별 프리셋

| direction | betaMax | sharpeMin | roeMin | debtRatioMax | tiers |
|-----------|---------|-----------|--------|-------------|-------|
| CONSERVATIVE | 0.8 | 0.3 | 5 | 150 | VERY_LOW, LOW |
| GROWTH | — | 0.2 | 8 | 250 | LOW, MODERATE, HIGH |
| IMPROVE | — | 0.2 | 5 | 200 | VERY_LOW, LOW, MODERATE |

Market 정규화: `KR` → `KR_KOSPI` + `KR_KOSDAQ`, `US` → `US_NYSE` + `US_NASDAQ` (서버측 자동 확장).

반환값은 명확한 필드명 사용 (약어 사용 안 함):

```json
{
  "candidates": [
    {
      "stockId": 123, "symbol": "005930", "name": "삼성전자",
      "sector": "Technology", "riskTier": "LOW",
      "beta": 0.85, "sharpe": 1.2, "per": 12.3, "roe": 15.2, "debtRatio": 45,
      "factors": { "size": 1.2, "value": -0.3, "momentum": 0.5, "volatility": -0.8, "quality": 0.9, "leverage": -0.4 },
      "vsSector": { "per": "cheap", "roe": "above_median" }
    }
  ],
  "totalMatched": 45
}
```

### 3-4. validate_portfolio — 리스크 검증

서버 계산:

```
포트폴리오 팩터 노출: B_p = Σ w_i × B_i (가중평균 z-score)
팩터 기반 변동성 추정: σ²_p ≈ B_p^T × Σ_f × B_p (factor_covariance 활용)
집중도: HHI = Σ w_i², effective N = 1/HHI
경고: 팩터 편향 > ±1.0σ, HHI > 0.25, 섹터 집중 > 40%
```

---

## 4. 사전 주입 컨텍스트 (Pre-injection)

`RecommendationContextBuilder`가 에이전트 시작 전에 서버측에서 계산하여 `userMessage`에 포함하는 데이터:

### 4-1. 보유 종목 테이블

컴팩트 테이블 포맷 + 약어 (시스템 프롬프트에 범례 포함):

```
15,200,000 KRW | PnL: +820,000 (+5.4%)

| id | Stock | Wt% | PnL% | Tier | β | Sh | PE | ROE | DR | f.sz | f.va | f.mo | f.vo | f.qu | f.le |
|---|-------|-----|------|------|---|----|----|-----|----|------|------|------|------|------|------|
| 123 | 삼성전자(005930) | 25.0 | +3.2 | LOW | 0.85 | 1.2 | 12.3 | 15.2 | 45 | 1.2 | -0.3 | 0.5 | -0.8 | 0.9 | -0.4 |
```

`findLatestByStockIds`로 indicators, fundamentals, factorExposure를 배치 조회.

### 4-2. 포트폴리오 리스크 평가

```
Factors: sz=1.05 va=-0.1 mo=0.7 vo=-0.3 qu=0.8 le=-0.2
Vol: 18.4% | HHI: 0.18 | EffN: 5.6
Warnings: 없음
```

기존 `evaluatePortfolio` 로직을 서버에서 사전 실행.

### 4-3. 섹터 개요

```
| Sector | N | mPE | mROE | mDR |
|--------|---|-----|------|-----|
| Technology | 145 | 15.2 | 12.3 | 85 |
```

`sectorAggRepo.findByMarketAndDate`로 해당 마켓의 모든 섹터 통계.

20종목 + 리스크 + 10섹터 = 약 800-1200 토큰.

---

## 5. 사용자 인터페이스

### 5-1. API

**추천 요청** (SSE)
```
GET /api/llm/portfolio-recommendation
    ?marketGroup=KR              (필수: KR | US)
    &lang=ko                     (선택: ko | en, 기본값 ko)
    &direction=IMPROVE           (선택: IMPROVE | CONSERVATIVE | GROWTH, 기본값 IMPROVE)
```

**히스토리 조회**
```
GET /api/llm/recommendation-history
    ?marketGroup=KR              (필수)
    &page=0                      (선택, 기본값 0)
    &size=10                     (선택, 기본값 10)
```

### 5-2. 입력 설계

대상 사용자(금융 초보)는 리스크 성향/섹터 선호를 직접 설정할 수 없다.

- `direction` 3종 프리셋으로 단순화: 전체 개선 / 안정 위주 / 수익 위주
- 리스크 성향은 기존 포트폴리오 구성에서 자동 유추
- `UserProfile`(투자경험, 연령대)을 서버에서 자동 로드하여 컨텍스트에 포함
- 프롬프트 인젝션 원천 차단: direction은 서버측 enum 검증

### 5-3. SSE 이벤트

| 이벤트 | 페이로드 | 설명 |
|--------|----------|------|
| `progress` | `{step, message}` | 단계 안내 |
| `thinking` | `{text}` | LLM 추론 텍스트 (200ms 버퍼링) |
| `tool_call` | `{tool, args}` | 도구 호출 시작 |
| `tool_result` | `{tool, summary, durationMs}` | 도구 실행 완료 |
| `result` | `RecommendationResponse` | 최종 추천 JSON |
| `error` | `{code?, message}` | 에러 |

progress step 종류:
- `ANALYZING_PORTFOLIO` — 사전 분석 중
- `SCREENING_STOCKS` — 후보 종목 검색 중
- `SEARCHING_MARKET` — 웹 검색 중
- `EVALUATING_PORTFOLIO` — 포트폴리오 리스크 검증 중
- `BUILDING_RECOMMENDATION` — 최종 추천 정리 중

### 5-4. 응답 JSON 구조

```json
{
  "current_assessment": "현재 포트폴리오에 대한 간단 평가 (2~3문장, 빈 포트폴리오면 null)",
  "stocks": [
    {
      "stock_id": 123,
      "symbol": "005930",
      "name": "삼성전자",
      "sector": "Technology",
      "allocation_percent": 25.0,
      "action": "KEEP",
      "reasoning": "이 종목에 대한 판단 이유 (2~3문장)"
    }
  ],
  "overall_reasoning": "전체 포트폴리오 구성 논리 (마크다운, 300~500자)"
}
```

action 값: `KEEP` (유지/비중 조정), `ADD` (신규 추가), `REMOVE` (매도 권유)

---

## 6. 시스템 프롬프트 전략

### 6-1. 분석 원칙 (MSCI/Barra 팩터 모델)

| 원칙 | 기준 |
|------|------|
| 섹터 분산 | 최소 3개 섹터, 단일 섹터 40% 이하 |
| 종목 집중도 | 단일 종목 30% 이하, HHI 0.25 이하 |
| 팩터 편향 | 6개 팩터 ±1.0σ 이상 편향 금지 |
| 포지션 사이징 | 기본 1%, 최대 2%, 총 heat 6-8% |

### 6-2. 처방적 2단계 워크플로우

시스템 프롬프트에서 고정 순서를 명시:

1. **1단계**: `find_candidates`로 후보 종목 검색 (병렬 호출 가능). 사전 주입된 데이터를 참고하여 섹터 선택. 기존 보유 종목은 `exclude_stock_ids`로 제외.
2. **2단계**: `validate_portfolio`로 최종 구성의 리스크 검증. warnings 있으면 조정, 없으면 바로 JSON 출력.

도구 호출 총 4회 이내.

### 6-3. 사전 주입 데이터 활용

프롬프트가 Agent에게 안내하는 내용:
- 포트폴리오 상세(종목별 지표, 팩터, 리스크 평가, 섹터 개요)가 **이미 포함**되어 있음
- 이 데이터를 바로 활용하여 추가 도구 호출 없이 분석할 것
- PER/ROE를 사전 제공된 섹터 중앙값과 비교할 것

---

## 7. 데이터 저장

```sql
create table if not exists public.portfolio_recommendations (
  id             bigserial primary key,
  user_id        uuid not null references public.users(id) on delete cascade,
  market_group   varchar(2) not null check (market_group in ('KR', 'US')),
  risk_tolerance varchar(10) not null,
  lang           varchar(2) not null default 'ko',
  stocks         jsonb not null,
  reasoning      text not null,
  model          varchar(50) not null,
  created_at     timestamptz not null default now()
);
```

- `risk_tolerance`는 `"ASSESSED"` 고정 (Agent가 포트폴리오에서 유추)

---

## 8. 비용 및 제한

| 항목 | 값 |
|------|-----|
| 1회 추천 비용 | 3 크레딧 |
| 모델 | Claude Opus (`app.llm.recommendation-model`) |
| Agent 최대 반복 | 4회 |
| API 호출 재시도 | 최대 2회 (지수 백오프) |
| Tool result 최대 길이 | 4,000자 |
| 최대 응답 토큰 | 3,000 |
| SSE 타임아웃 | 무제한 (SseEmitter -1L) |
| 클라이언트 타임아웃 | 360초 |
| web_search 최대 사용 | 2회 |

---

## 9. 파일 구조

```
feature/recommendation/
├── controller/
│   └── RecommendationController.kt      # SSE endpoint + 히스토리
├── dto/
│   └── RecommendationDtos.kt            # Request, Response, SSE 이벤트 DTO
└── service/
    ├── RecommendationAgentService.kt    # Agent Loop (스트리밍 + 도구 반복)
    ├── RecommendationContextBuilder.kt  # 사전 계산 (보유 종목 + 리스크 + 섹터)
    ├── RecommendationToolExecutor.kt    # find_candidates + validate_portfolio 실행
    ├── RecommendationToolDefinitions.kt # 도구 스키마 정의 (2개 커스텀 + web search)
    └── RecommendationPrompts.kt         # 시스템 프롬프트 + 사전 주입 컨텍스트 포맷

domain/entity/recommendation/
└── PortfolioRecommendation.kt

domain/repository/recommendation/
└── PortfolioRecommendationRepository.kt
```

---

## 10. 운영 안정성

### HTTP 클라이언트
`AnthropicOkHttpClient`는 `lazy` 싱글턴으로 커넥션 풀 재사용.

### 재시도
`streamWithRetry` — 최대 2회 재시도, 지수 백오프 `(1 shl attempt) * 1000ms`.

### Disconnect 감지
`SseEmitter.onCompletion/onTimeout/onError` → `AtomicBoolean cancelled`. 매 iteration + 도구 실행 전 체크. 클라이언트 이탈 시 즉시 중단.

### 크레딧 원자성
`checkAndIncrementBy(userId, 3)` 단일 트랜잭션. Agent 실패 시 `decrementBy`로 자동 복원.

### Context Window 관리
- `truncateResult` — 4,000자 초과 시 truncate.
- 사전 주입으로 도구 호출 횟수 감소 → 대화 이력 비대화 방지.
- 보유종목 15개 이상 시 사전 주입 데이터 압축 검토 필요.

### JSON 파싱
`extractJson` — fenced code block 또는 brace-counting 방식으로 중첩 JSON 추출.

### 캐시
`find_candidates` → `DashboardService.list(filter)` 경유 → `@Cacheable("screener")` 적용.

### 배치 쿼리
사전 계산과 도구 실행 모두 `findLatestByStockIds` 배치 쿼리 사용. N+1 문제 없음.

---

## 11. 제약 사항 및 Edge Case

| 상황 | 동작 |
|------|------|
| 빈 포트폴리오 | 사전 주입 스킵. Agent가 처음부터 포트폴리오 구성 |
| 팩터 데이터 미존재 | 해당 종목 팩터 노출 = 0으로 처리 |
| Claude API 장애 | 2회 재시도 후 실패 → 크레딧 복원 + error SSE |
| 사용자 SSE 중단 | cancelled 플래그 → Agent 루프 즉시 중단 |
| 4회 내 JSON 미출력 | 부분 JSON 추출 시도 → 실패 시 안내 메시지 |
| Tool result 4,000자 초과 | truncate (Claude에 안내 포함) |
| find_candidates 0건 반환 | 섹터 제한 완화하여 자동 재시도 |
| risk_badge 없는 종목 | LEFT JOIN으로 badge 없어도 스크리닝에 포함 |
| 탭 전환 중 세션 재검증 | AuthProvider가 401만 clear, 네트워크 오류는 기존 user 유지 |

---

## 12. 프론트엔드 연동

### 12-1. SSE 클라이언트
- `fetch + ReadableStream` 기반 (`EventSource`는 커스텀 헤더 미지원)
- `SSEError` 타입: `AUTH_EXPIRED`, `CREDIT_EXCEEDED`, `RATE_LIMITED`, `SERVER_ERROR`, `NETWORK_ERROR`, `TIMEOUT`
- 360초 클라이언트 타임아웃
- 자동 재연결 없음 (Agent 비멱등) — retryable 에러 시 재시도 버튼 노출

### 12-2. 탭 전환 안정성
- `AuthProvider.fetchUser()`는 네트워크 오류 시 기존 user를 유지하고, 명시적 401 응답만 `setUser(null)` 수행
- SSE 스트림 진행 중 탭 전환해도 상태 초기화 없음

### 12-3. 컴포넌트 구조
- `RecommendationSection`: direction 프리셋 3개 + 스트리밍 + 도구 호출 표시 + 결과 카드
- `RecommendationHistory`: 날짜별 접이식 목록
- 페이지 재진입 시 빈 상태 시작 — 이전 결과는 History에서만 확인

### 12-4. 도구 표시 라벨

| 도구 | 한국어 | English |
|------|--------|---------|
| `find_candidates` | 종목 검색 | Finding candidates |
| `validate_portfolio` | 리스크 검증 | Risk validation |
| `web_search` | 웹 검색 | Web search |

---

## 13. 설정

```properties
app.llm.recommendation-model=claude-opus-4-6
app.llm.daily-limit=20
```

웹 검색은 Anthropic 빌트인 web search tool 사용 (별도 API 키 불필요).

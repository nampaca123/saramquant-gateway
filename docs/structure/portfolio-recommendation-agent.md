# 포트폴리오 추천 AI Agent 설계 문서

> Claude Tool Use(Function Calling)를 활용한 자율형 AI Agent가 사용자의 기존 포트폴리오를 분석하고 개선 방안을 추천하는 기능

---

## 1. 개요

### 1-1. 목적

기존 LLM 통합은 **해석** 역할이었다면, 포트폴리오 추천 Agent는 **의사결정 보조** 역할이다.

- 기존: "이 종목의 리스크 특성은 ~입니다" (수동적 해석)
- 추천 Agent: "현재 포트폴리오는 기술주에 편중되어 있으니, ~를 추가하면 분산 효과를 높일 수 있습니다" (능동적 제안)

Agent는 도구(screen_stocks, get_stock_detail 등)를 **자율적으로** 선택하여 데이터를 조회하고, MSCI/Barra 팩터 모델 기반으로 분산 투자를 검증한 뒤 추천을 생성한다.

### 1-2. 기존 LLM 기능과의 차이

| 구분 | 종목/포트폴리오 분석 | 포트폴리오 추천 Agent |
|------|---------------------|---------------------|
| 패턴 | 단일 LLM 호출 | Agent Loop (다중 호출 + 도구 실행) |
| 모델 | Sonnet / Opus | Opus (복합 추론 필수) |
| 입력 | 서버가 조립한 컨텍스트 | 사용자 포트폴리오 + Agent가 도구로 수집 |
| 출력 | 자연어 분석 리포트 | 구조화된 JSON (종목 + 비중 + 근거) |
| 비용 | 1 크레딧 | 3 크레딧 |
| 응답 방식 | 동기 HTTP | SSE 스트리밍 (진행 상황 실시간 표시) |

---

## 2. 아키텍처

### 2-1. 요청 흐름

```
[Frontend 포트폴리오 페이지]
    ↓ GET /api/llm/portfolio-recommendation?marketGroup=KR&lang=ko (SSE)
    
RecommendationController
    ├── 입력 검증 (marketGroup: KR|US, lang: ko|en)
    ├── checkAndIncrementBy(userId, 3) — 원자적 크레딧 차감
    ├── PortfolioService.getPortfolioDetail() → 기존 보유 종목 로드
    └── llmExecutor.execute() → 비동기 Agent 실행
         └── 실패 시 decrementBy(userId, 3) — 크레딧 자동 복원
    
RecommendationAgentService (Agent Loop)
    ├── 0. 클라이언트 disconnect 감지 (AtomicBoolean cancelled)
    ├── 1. 사용자 포트폴리오를 user message에 포함
    ├── 2. Claude API 호출 (tools 포함, 재시도 2회, 타임아웃 적용)
    ├── 3. Claude가 tool_use 응답 → ToolExecutor로 실행
    ├── 4. tool_result를 truncate 후 Claude에 반환
    ├── 5. 2~4 반복 (최대 10회, 매 iteration disconnect 체크)
    ├── 6. Claude가 최종 JSON 응답 (brace-counting 파서로 추출)
    ├── 7. DB 저장 + SSE result 이벤트
    └── 매 도구 실행마다 SSE progress 이벤트 전송

    ↓ SSE events
[Frontend] ← progress: "삼성전자의 상세 데이터를 확인하고 있습니다..."
[Frontend] ← progress: "포트폴리오 리스크를 검증하고 있습니다..."
[Frontend] ← result: { stocks: [...], overall_reasoning: "..." }
```

### 2-2. 컨텍스트 흐름

```
사용자의 기존 포트폴리오 (PortfolioDetail)
    │
    ├── 보유 종목: symbol, name, sector, shares, avgPrice
    ├── 현재가, 손익, 손익률
    ├── 리스크 등급 (summaryTier)
    └── 총 평가액, 총 손익
    │
    ↓ RecommendationPrompts.userMessage()로 테이블 포맷
    │
Claude System Prompt (분석 원칙 + 도구 사용 지침)
    +
User Message (포트폴리오 테이블 + 사용자 메시지)
    │
    ↓ Agent가 도구로 추가 데이터 수집
    │
최종 추천 JSON (KEEP / ADD / REMOVE + 비중 + 근거)
```

---

## 3. 도구 설계 (5개)

### 3-1. 설계 원칙

- **팩터 모델 기반**: 기관 포트폴리오 구성 프로세스(MSCI/Barra)를 참고하여, Claude가 팩터 기반 분산 투자를 추론할 수 있도록 데이터 제공
- **넓은 탐색 → 깊은 분석 분리**: screen_stocks(20개+ 넓은 데이터) vs get_stock_detail(1개 깊은 데이터)로 토큰 효율성 확보
- **검증 도구 필수**: evaluate_portfolio로 최종 추천 전 리스크를 정량 검증
- **자율적 사용**: 고정 순서 없이, 원칙만 제시하고 Claude가 상황에 따라 도구 선택

### 3-2. 도구 목록

| 도구 | 역할 | 입력 | 출력 |
|------|------|------|------|
| `screen_stocks` | 리스크 성향에 맞는 후보 종목 필터링 | market, tiers, sector, beta/sharpe/roe 등 필터 | 종목 리스트 (symbol, sector, tier, beta, sharpe, PER 등) |
| `get_stock_detail` | 후보 종목 심층 분석 | stock_id | 기술지표, 펀더멘털, 팩터 노출 6개 z-score, 리스크 뱃지, 섹터 비교, 무위험이자율 |
| `get_sector_overview` | 시장 전체 섹터 구조 파악 | market | 섹터별 종목 수, median PER/PBR/ROE/부채비율 |
| `evaluate_portfolio` | 후보 포트폴리오 리스크 정량 검증 | stocks [{stock_id, weight}] | 팩터 노출, 추정 변동성(σ²_p ≈ B_p^T × Σ_f × B_p), 집중도(HHI), 가중평균 지표, 팩터 편향 경고 |
| `web_search` (빌트인) | 최신 시장 동향/뉴스 검색 | Claude가 자율적으로 호출 | Anthropic 서버에서 자동 처리 (별도 API 키 불필요) |

### 3-3. evaluate_portfolio 상세 — 핵심 도구

서버에서 수행하는 계산:

```
포트폴리오 팩터 노출: B_p = Σ w_i × B_i (가중평균 z-score)
팩터 기반 변동성 추정: σ²_p ≈ B_p^T × Σ_f × B_p (factor_covariance 테이블 활용)
집중도: HHI = Σ w_i², effective N = 1/HHI
경고: 팩터 편향 > ±1.0σ, HHI > 0.25, 섹터 집중 > 40%
```

Claude는 수학 계산을 하지 않고, 서버가 계산한 결과만 해석하여 비중 조정 여부를 판단한다.

---

## 4. 사용자 인터페이스

### 4-1. API

**추천 요청** (SSE)
```
GET /api/llm/portfolio-recommendation
    ?marketGroup=KR        (필수: KR | US)
    &lang=ko               (선택: ko | en, 기본값 ko)
    &message=좀 더 안정적으로  (선택: 자연어 추가 요청)
```

**히스토리 조회**
```
GET /api/llm/recommendation-history
    ?marketGroup=KR        (필수)
    &page=0                (선택, 기본값 0)
    &size=10               (선택, 기본값 10)
```

### 4-2. 입력 설계 — 왜 간단한가

대상 사용자(금융 초보 2030)는 리스크 성향, 섹터 선호, 희망 종목 수 같은 파라미터를 알지 못한다.

| 기존 설계 (폐기) | 현재 설계 |
|-----------------|-----------|
| riskTolerance (LOW/MODERATE/HIGH) | 기존 포트폴리오에서 자동 유추 |
| investmentAmount | 기존 보유 금액에서 자동 파악 |
| sectorPreferences | Agent가 분석 후 판단 |
| stockCount | Agent가 분산 원칙에 따라 결정 |
| → 사용자가 전문 용어 입력 필요 | → 사용자는 "추천해줘" 한마디로 충분 |

사용자가 추가 의사를 표현하고 싶으면 `message` 파라미터에 자연어로 입력:
- "좀 더 안정적으로 바꿔줘"
- "기술주 비중을 줄이고 싶어"
- "배당주 중심으로"

### 4-3. SSE 이벤트

| 이벤트 | 데이터 | 설명 |
|--------|--------|------|
| `progress` | `{"step": "ANALYZING_STOCK", "message": "삼성전자의 상세 데이터를 확인하고 있습니다..."}` | Agent가 도구를 사용할 때마다 전송 |
| `result` | `{"stocks": [...], "overallReasoning": "...", "currentAssessment": "..."}` | 최종 추천 결과 |
| `error` | `{"message": "Daily usage limit exceeded"}` | 오류 발생 시 |

progress step 종류:
- `SEARCHING_MARKET` — 웹 검색 중
- `ANALYZING_SECTORS` — 섹터 분석 중
- `SCREENING_STOCKS` — 종목 스크리닝 중
- `ANALYZING_STOCK` — 개별 종목 분석 중 (종목명 포함)
- `EVALUATING_PORTFOLIO` — 포트폴리오 리스크 검증 중
- `BUILDING_RECOMMENDATION` — 최종 추천 정리 중
- `PROCESSING` — 기타 처리 중

### 4-4. 응답 JSON 구조

```json
{
  "current_assessment": "현재 포트폴리오는 기술주에 60% 집중되어 있어 분산이 부족합니다. 전체적으로 HIGH 리스크 종목 비중이 높습니다.",
  "stocks": [
    {
      "stock_id": 123,
      "symbol": "005930",
      "name": "삼성전자",
      "sector": "Technology",
      "allocation_percent": 25.0,
      "action": "KEEP",
      "reasoning": "현재 보유 중이며 밸류에이션이 적정합니다. 다만 비중을 30%에서 25%로 줄여 집중도를 낮추는 것이 좋겠습니다."
    },
    {
      "stock_id": 456,
      "symbol": "035720",
      "name": "카카오",
      "sector": "Communication Services",
      "allocation_percent": 0,
      "action": "REMOVE",
      "reasoning": "부채비율이 섹터 중앙값 대비 높고, 모멘텀 팩터가 -1.2σ로 하락 추세입니다."
    },
    {
      "stock_id": 789,
      "symbol": "006400",
      "name": "삼성SDI",
      "sector": "Industrials",
      "allocation_percent": 20.0,
      "action": "ADD",
      "reasoning": "포트폴리오에 산업재 섹터가 부족합니다. ROE 12%, 부채비율 80%로 펀더멘털이 탄탄합니다."
    }
  ],
  "overall_reasoning": "...(300~500자 마크다운)..."
}
```

action 값:
- `KEEP` — 기존 보유 유지 (비중 조정 가능)
- `ADD` — 새로 추가 추천
- `REMOVE` — 기존 보유 중 매도 권유

---

## 5. 시스템 프롬프트 전략

### 5-1. 분석 원칙 (MSCI/Barra 팩터 모델 기반)

| 원칙 | 기준 |
|------|------|
| 섹터 분산 | 최소 3개 섹터, 단일 섹터 40% 이하 |
| 종목 집중도 | 단일 종목 30% 이하, HHI 0.25 이하 |
| 팩터 편향 | 6개 팩터 중 ±1.0σ 이상 편향 금지 |
| 종목 필터 | ROE > 5%, 부채비율 < 200% |
| 포지션 사이징 | 기본 1%, 최대 2%, 총 heat 6-8% |

### 5-2. 리스크 성향 — 자동 유추

사용자에게 리스크 성향을 묻지 않는다. Agent가 기존 포트폴리오 구성에서 유추:
- 고베타/고변동성 종목 위주 → HIGH로 판단
- 저변동성/배당주 위주 → LOW로 판단
- 빈 포트폴리오 → MODERATE 기본값

사용자가 `message`로 방향을 명시하면 그에 따라 조정.

### 5-3. 도구 사용 — 자율적 판단

고정 워크플로우 없이 원칙만 제시:
- 숫자 기반 분석이 우선 (팩터, 밸류에이션, 리스크 지표)
- 웹 검색은 필요할 때만 (특정 종목 이슈 확인, 시장 동향 검증)
- 최종 추천 전 반드시 `evaluate_portfolio`로 리스크 검증
- 팩터 편향 경고 시 비중 조정 후 재검증

---

## 6. 데이터 저장

### 6-1. portfolio_recommendations 테이블

```sql
create table if not exists public.portfolio_recommendations (
  id             bigserial primary key,
  user_id        uuid not null references public.users(id) on delete cascade,
  market_group   varchar(2) not null check (market_group in ('KR', 'US')),
  risk_tolerance varchar(10) not null,
  lang           varchar(2) not null default 'ko',
  stocks         jsonb not null,       -- 추천 종목 리스트
  reasoning      text not null,        -- overall_reasoning
  model          varchar(50) not null,  -- 사용된 모델명
  created_at     timestamptz not null default now()
);
```

- 추천 결과를 사용자별로 저장하여 히스토리 페이지에서 조회 가능
- `risk_tolerance`는 Agent가 유추한 값("ASSESSED")을 저장

---

## 7. 비용 및 제한

| 항목 | 값 |
|------|-----|
| 1회 추천 비용 | 3 크레딧 (일일 한도 20 중 3 차감) |
| 모델 | Claude Opus (`app.llm.recommendation-model`, 기본값 `claude-opus-4-6`) |
| SSE 타임아웃 | 150초 (2.5분) |
| Agent 최대 반복 | 10회 |
| API 호출 재시도 | 최대 2회 (지수 백오프) |
| 호출 타임아웃 | `app.llm.claude.timeout` (기본 30초) |
| Tool result 최대 길이 | 6,000자 (초과 시 truncate) |
| 최대 토큰 | 4096 (응답) |

---

## 8. 파일 구조

```
feature/recommendation/
├── controller/
│   └── RecommendationController.kt      # SSE endpoint + 히스토리
├── dto/
│   └── RecommendationDtos.kt            # Request, Response, ProgressEvent
└── service/
    ├── RecommendationAgentService.kt    # Agent Loop (Claude API + 도구 반복)
    ├── RecommendationToolExecutor.kt    # 4개 커스텀 도구 실행 (DB 조회 + 팩터 계산)
    ├── RecommendationToolDefinitions.kt # 도구 스키마 정의 (커스텀 4개 + 빌트인 web search)
    └── RecommendationPrompts.kt         # 시스템 프롬프트 + 포트폴리오 컨텍스트 포맷

domain/entity/recommendation/
└── PortfolioRecommendation.kt           # JPA Entity

domain/repository/recommendation/
└── PortfolioRecommendationRepository.kt
```

---

## 9. 설정

```properties
# application.properties
app.llm.recommendation-model=claude-opus-4-6   # 추천 전용 모델
app.llm.daily-limit=20                         # 일일 크레딧 한도
```

웹 검색은 Anthropic 빌트인 web search tool을 사용하므로 별도 API 키 불필요 (Anthropic API 키로 동작).

---

## 10. 운영 안정성 (Production Resilience)

> 아래는 같은 패턴의 Agent 기능을 구현할 때 반드시 적용해야 할 체크리스트이다.

### 10-1. HTTP 클라이언트 관리

- `AnthropicOkHttpClient`는 **요청마다 생성하지 않는다**. 내부에 커넥션 풀 · 스레드 풀을 갖고 있으므로 `lazy` 싱글턴으로 재사용.
- `timeout`은 `LlmProperties.claude.timeout`을 따른다.

### 10-2. 재시도 (Retry)

- `callWithRetry` — 개별 Claude API 호출을 최대 `MAX_API_RETRIES`(2)회 재시도.
- 지수 백오프: `(1 shl attempt) * 1000ms`. 일시적 네트워크/서버 오류에 대응.
- 비즈니스 에러(invalid request 등)는 재시도 없이 즉시 throw.

### 10-3. 클라이언트 Disconnect 감지

- `SseEmitter.onCompletion / onTimeout / onError` 콜백으로 `AtomicBoolean cancelled`를 설정.
- Agent 루프 매 iteration + 매 도구 실행 전 `cancelled.get()` 체크.
- 클라이언트 이탈 시 불필요한 API 호출 중단 → 비용 절감.

### 10-4. 크레딧 원자성 + 실패 복원

- `checkAndIncrementBy(userId, 3)` — 잔량 확인 + 차감을 **단일 트랜잭션**으로 처리. Race condition 방지.
- Agent가 `false`(실패)를 반환하면 `decrementBy(userId, 3)`으로 크레딧 복원.
- 포트폴리오 조회 실패처럼 Agent 실행 전 에러도 즉시 복원.

### 10-5. Context Window 관리

- `truncateResult` — 도구 실행 결과가 `MAX_TOOL_RESULT_CHARS`(6,000)자를 초과하면 잘라서 전달.
- `screen_stocks` 출력에서 `latestClose`/`changePct` 등 스크리닝에 불필요한 필드를 제외하여 토큰 효율화.
- Agent 루프가 10회까지 돌 수 있으므로 대화 이력이 비대해질 수 있음에 주의.

### 10-6. JSON 파싱 견고화

- `extractJson` — brace-counting 방식으로 중첩 JSON도 정확히 추출.
- `{` 시작부터 depth를 추적해 매칭되는 `}` 위치를 계산. 기존 `lastIndexOf` 대비 안정적.

### 10-7. 에러 직렬화 통일

- 모든 SSE 에러 이벤트를 `objectMapper.writeValueAsString`으로 직렬화.
- 기존 `replace("\"", "'")`로 깨질 수 있던 문자열 보간 방식 폐기.

### 10-8. 캐시 활용

- `screen_stocks` 도구는 `DashboardService.list(filter)`를 경유하여 `@Cacheable("screener")` 혜택 적용.
- 동일 필터 조건의 반복 스크리닝 시 DB 부하를 회피.

### 10-9. 배치 쿼리

- `evaluatePortfolio`에서 종목별 N+1 쿼리를 `findLatestByStockIds` 배치 쿼리로 교체.
- 8종목 기준: 24개 쿼리 → 3개 쿼리로 감소.

---

## 11. 제약 사항 및 Edge Case

| 상황 | 동작 |
|------|------|
| 빈 포트폴리오 | Agent가 처음부터 포트폴리오를 구성 (시스템 프롬프트에 명시) |
| 팩터 데이터 미존재 종목 | `evaluate_portfolio`에서 해당 종목의 팩터 노출 = 0으로 처리 |
| Claude API 전체 장애 | 2회 재시도 후 실패 → 크레딧 복원 + error SSE 이벤트 |
| 사용자가 SSE 연결 중단 | `cancelled` 플래그로 Agent 루프 즉시 중단 |
| Agent가 10회 내 JSON 미출력 | "Agent exceeded max iterations" 에러 + 크레딧 복원 |
| Tool result가 매우 클 때 | 6,000자 초과분 truncate (Claude에 안내 메시지 포함) |
| 동일 사용자 동시 요청 | `checkAndIncrementBy` 원자적 차감으로 크레딧 초과 방지 |

---

## 12. 프론트엔드 연동 (v2)

### 12-1. 입력 방식
- 자유 텍스트 `message` 폐기 → `RecommendationDirection` enum (`IMPROVE`, `CONSERVATIVE`, `GROWTH`)
- 프롬프트 인젝션 원천 차단: direction은 서버에서 enum 검증, 고정 텍스트 매핑
- `UserProfile`(투자경험, 연령대)을 서버에서 자동 로드하여 프롬프트에 주입

### 12-2. SSE 클라이언트 (v3 — 스트리밍 강화)
- `fetch + ReadableStream` 기반 (`EventSource`는 커스텀 헤더 미지원)
- `SSEError` 타입으로 에러 분류: `AUTH_EXPIRED`, `CREDIT_EXCEEDED`, `RATE_LIMITED`, `SERVER_ERROR`, `NETWORK_ERROR`, `TIMEOUT`
- 360초 클라이언트 타임아웃 (Opus 최악 케이스 7×40s + 도구 실행 여유)
- SseEmitter 타임아웃: `-1L` (무제한) — 스트리밍으로 지속 이벤트 발송, 절대 타임아웃 불필요
- 자동 재연결 없음 (Agent 비멱등) — retryable 에러 시 재시도 버튼 노출

### 12-3. SSE 이벤트 타입
| 이벤트 | 설명 | 페이로드 |
|--------|------|----------|
| `progress` | 도구 실행 단계 안내 | `{step, message}` |
| `thinking` | LLM 추론 텍스트 (200ms 버퍼링) | `{text}` |
| `tool_call` | 도구 호출 시작 | `{tool, args}` |
| `tool_result` | 도구 실행 완료 | `{tool, summary, durationMs}` |
| `result` | 최종 추천 JSON | `RecommendationResponse` |
| `error` | 에러 | `{code?, message}` |

### 12-4. 스트리밍 아키텍처 (v3)
- `client.messages().create()` → `createStreaming()` 전환 (Anthropic Java SDK 2.19.0)
- `RawMessageStreamEvent` 이벤트 루프: `ContentBlockStart`, `ContentBlockDelta`(TextDelta / InputJsonDelta), `ContentBlockStop`, `MessageDelta`
- **thinking 버퍼링**: 토큰별 전송 대신 200ms 간격으로 묶어 전송. 프론트엔드 렌더링 부하 억제.
- **병렬 도구 실행**: Claude가 한 턴에 보낸 도구는 독립으로 신뢰, `CompletableFuture.supplyAsync()` 병렬 실행
- **하트비트**: 도구 실행 구간에만 5초 간격 SSE comment — 스트리밍 중에는 토큰 델타가 연속이므로 불필요
- **MAX_ITERATIONS**: 10 → 7, 프롬프트에 도구 일괄 호출 유도 + 7회 이내 제한 명시
- **graceful degradation**: 7회 도달 시 부분 JSON 추출 시도 → 성공 시 반환, 실패 시 안내 메시지

### 12-5. 크레딧 복원 정책
- 기준: `result` SSE 이벤트 전송 성공 여부
- `recommend()` → `false` 반환 시 자동 복원 (기존 구현)
- 부분 성공(progress만 수신, result 미도달)도 복원 대상
- 클라이언트 disconnect → `cancelled` 플래그 → 스트리밍 `takeWhile` 중단 → 루프 종료 → 복원

### 12-6. 빈 포트폴리오 지원
- Controller에서 portfolio null 시 빈 `PortfolioDetail`을 Agent에 전달
- Prompts에 빈 포트폴리오 안내 문구 포함 (기존 `buildPortfolioContext` 활용)
- 프론트엔드: 프리셋 "전체 개선" → "포트폴리오 구성"으로 라벨 변경

### 12-7. 컴포넌트 구조
- `RecommendationSection`: 프리셋 3개 + 실시간 스트리밍 텍스트 + 도구 호출/결과 표시 + 결과 카드 + 에러/재시도
- `StreamingView`: LLM 추론 텍스트 실시간 표시, 도구 호출 진행 상태, auto-scroll
- `RecommendationHistory`: 날짜별 접이식 목록 (AnalysisHistory 패턴 답습)
- 페이지 재진입 시 항상 빈 상태 시작 — 이전 결과는 History에서만 확인

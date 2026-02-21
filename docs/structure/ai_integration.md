# AI/LLM 통합 아키텍처 문서

> saramquant-gateway에서 LLM(대규모 언어 모델)을 활용한 전략 해석 기능의 설계 결정 및 비용 관리 전략 기록

---

## 1. LLM 통합 아키텍처 개요

### 1-1. 목적

LLM은 **챗봇이 아니다.** 사용자와 자유롭게 대화하는 것이 아니라, 퀀트 파이프라인의 끝단에서 **계산 결과를 자연어로 해석**해주는 역할이다.

- calc-server가 지표를 계산하고 DB에 저장
- gateway가 지표를 조회하여 LLM에 전달
- LLM이 "이 종목의 리스크 특성은 ~이며, ~에 주의해야 합니다" 형태로 해석
- 사용자는 숫자가 아닌 **맥락 있는 설명**을 받는다

### 1-2. 요청 흐름

```
Client → Controller → Service (캐시 확인)
                         ↓ 캐시 miss
                      PromptBuilder (컨텍스트 조립)
                         ↓
                      LlmRouter (모델 선택 + 호출)
                         ↓
                      DB 캐시 저장 (StockAiAnalysis)
                         ↓
                      응답 반환 (+ 면책 문구 append)
```

캐시 hit이면 DB에서 바로 반환하므로 LLM 호출 비용이 발생하지 않는다.

### 1-3. 모델 분리 전략

| 기능 | 모델 | 이유 |
|------|------|------|
| 종목 분석 | Claude Sonnet 4.6 | 단일 종목 컨텍스트는 비교적 단순, 빠른 응답과 낮은 비용 우선 |
| 포트폴리오 진단 | Claude Opus 4.6 | 다수 종목 간 상관관계, 분산 효과, 리밸런싱 제안 등 복합 추론 필요 |

모델 선택은 `LlmRouter`에서 요청 타입(종목/포트폴리오)에 따라 자동으로 결정된다.

---

## 2. 비용 관리 전략 (7중 방어)

LLM API 호출은 건당 비용이 발생하므로, 예상치 못한 비용 폭발을 막기 위해 7겹의 방어 장치를 설계했다.

### ① 캐싱 — 같은 분석을 두 번 생성하지 않음

- 캐시 키: `stock_id + date + preset + lang`
- 하루에 한 번만 생성하면 이후 같은 요청은 DB에서 반환
- 지표가 매일 갱신되므로 날짜를 키에 포함하여 최신성 보장

### ② 일일 사용 한도

- 사용자당 하루 20회 AI 분석 요청 제한
- `AiUsageLog` 테이블에서 PostgreSQL `INSERT ... ON CONFLICT DO UPDATE` (upsert)로 atomic increment
- 동시 요청이 와도 race condition 없이 정확한 카운트 보장

### ③ 비로그인자 POST 차단

- AI 분석 요청(`POST`)은 인증된 사용자만 가능
- `SecurityConfig`에서 해당 경로를 `authenticated()` 설정
- GET(캐시된 분석 조회)은 비로그인자도 가능하여 SEO/공유에 유리

### ④ 프리셋만 허용 — 자유 채팅 불가

- 사용자가 임의의 프롬프트를 보낼 수 없음
- 미리 정의된 프리셋만 허용: `general`, `risk_focused`, `financial_weakness`, `aggressive` 등
- 프리셋 외 값이 들어오면 400 Bad Request 반환
- 토큰 낭비와 프롬프트 인젝션을 원천 차단

### ⑤ 모델 분리 — 비용 효율적 모델 우선

- 위 1-3에서 설명한 대로, 단순한 요청에는 저렴한 모델 사용
- 불필요하게 고급 모델을 호출하는 비용 낭비 방지

### ⑥ Thundering Herd 방지

동일한 분석을 여러 사용자가 동시에 요청하면 LLM이 중복 호출된다.

- `ConcurrentHashMap<CacheKey, CompletableFuture<String>>`으로 진행 중인 요청 추적
- 첫 번째 요청만 LLM을 호출하고, 나머지는 같은 `CompletableFuture`를 공유하여 대기
- 결과가 오면 모든 대기 요청에 동시 반환
- 캐시 저장 후 Map에서 키 제거

### ⑦ 캐시 TTL — 오래된 분석 자동 삭제

- `AiCacheCleanupScheduler`가 매일 새벽에 실행
- 30일 초과된 `StockAiAnalysis` 레코드를 삭제
- 스토리지 비용 절감 + 오래된 분석이 노출되는 것 방지

---

## 3. Retry + Fallback 전략

LLM API는 외부 서비스이므로 장애가 발생할 수 있다. 3단계 재시도/폴백 전략으로 가용성을 확보한다.

| 단계 | 대상 | 대기 | 비고 |
|------|------|------|------|
| 1차 시도 | Claude (primary) | — | 정상 경로 |
| 1차 실패 후 | Claude (same model) | 2초 | 일시적 오류 대비 재시도 |
| 2차 실패 후 | OpenAI GPT-5.2 (fallback) | 4초 | 완전히 다른 프로바이더로 전환 |
| 3차 실패 후 | — | — | 503 Service Unavailable 반환 |

**구현 구조:**

- `LlmClient` 인터페이스 — `suspend fun generate(prompt: LlmPrompt): String`
- `AnthropicClient` — Claude API 호출 구현 (메시지 형식, 헤더, 모델 파라미터)
- `OpenAiClient` — GPT API 호출 구현 (chat completions 형식)
- `LlmRouter` — retry 로직 + fallback 순서 관리

각 클라이언트가 API 구조 차이(Anthropic의 `messages` vs OpenAI의 `chat/completions`)를 내부적으로 처리하므로, `LlmRouter`는 프로바이더 차이를 알 필요가 없다.

---

## 4. 프롬프트 설계

### 4-1. 시스템 프롬프트 — 6개 원칙

LLM의 응답 품질과 법적 안전성을 위해 시스템 프롬프트에 6가지 원칙을 명시한다:

1. **매수/매도 추천 금지** — 투자 자문업 라이선스 없이 구체적 행동 권유는 법적 리스크
2. **쉬운 말 사용** — 전문 용어를 사용하되 괄호 안에 쉬운 설명 병기
3. **숫자 근거 제시** — "위험합니다" 대신 "베타 1.8로 시장 평균(1.0)의 1.8배 변동성"
4. **공포/탐욕 조장 금지** — "지금 안 사면 늦습니다" 같은 표현 차단
5. **3문단 제한** — 응답이 너무 길면 사용자가 읽지 않음, 핵심만 전달
6. **언어 일치** — `lang` 파라미터에 맞는 언어로만 응답

### 4-2. 종목 분석 컨텍스트

PromptBuilder가 다음 데이터를 조합하여 유저 프롬프트에 주입한다:

- **기술적 지표:** beta, sharpe, volatility, max_drawdown, VaR 등
- **펀더멘털:** PER, PBR, ROE, 부채비율, 영업이익률 등
- **섹터 비교:** 해당 종목이 속한 섹터의 평균 지표와 비교
- **팩터 노출도:** 시장(MKT), 규모(SMB), 가치(HML) 등 팩터 계수
- **무위험 이자율:** 현재 기준금리 (리스크 프리미엄 맥락 제공)

이 모든 숫자를 제공함으로써 LLM이 할루시네이션 없이 **실제 데이터 기반**으로 해석할 수 있다.

### 4-3. 포트폴리오 진단 컨텍스트

- **holdings table:** 종목별 비중, 수익률, 리스크 뱃지
- **포트폴리오 리스크 점수:** 가중평균 기반 종합 점수
- **리스크 분해:** 어떤 종목이 전체 리스크에 기여하는 비율
- **분산도:** 섹터 집중도, 상관관계 행렬 요약

### 4-4. 프리셋별 확장 컨텍스트

기본 프리셋(`general`, `risk_focused`)은 위의 표준 컨텍스트만 사용한다.
특수 프리셋은 추가 데이터를 주입한다:

| 프리셋 | 추가 컨텍스트 |
|--------|---------------|
| `financial_weakness` | 종목별 상세 재무제표 데이터 (유동비율, 이자보상배율, 잉여현금흐름 등) |
| `aggressive` | 종목별 모멘텀 지표, 거래량 급증 여부, 레버리지 수준 |

추가 데이터가 많아질수록 토큰 소비가 증가하므로, 필요한 프리셋에서만 선택적으로 주입한다.

### 4-5. i18n (다국어 지원)

- `lang` 파라미터: `ko`, `en` 등
- 시스템 프롬프트, 유저 프롬프트 모두 `lang`에 맞게 분기
- 캐시 키에 `lang` 포함 → 같은 종목이라도 한국어/영어 분석이 별도 저장
- 면책 문구도 언어별로 다른 텍스트 사용

---

## 5. 면책 문구 전략

### 5-1. Gateway에서 고정 append

AI 분석 응답 끝에 면책 문구를 붙이는 주체는 **LLM이 아니라 Gateway**이다.

```
[AI 분석 결과]
...LLM이 생성한 텍스트...

---
본 분석은 투자 자문이 아니며, 투자 결정의 책임은 사용자 본인에게 있습니다.
```

### 5-2. LLM에게 맡기지 않는 이유

- LLM은 확률적 모델이므로 면책 문구를 **누락하거나 변형**할 수 있다
- 시스템 프롬프트에 "반드시 면책 문구를 넣어라"고 해도 100% 보장 불가
- 면책 문구는 법적 요소이므로 **결정론적(deterministic)** 방식으로 처리해야 안전
- Gateway 코드에서 문자열 concat으로 붙이면 누락 확률 = 0

---

## 6. 파일 구조

### 6-1. infra/ai/ — LLM 통신 인프라

```
infra/ai/
├── LlmClient.kt              ← 인터페이스 (추상화 계층)
├── AnthropicClient.kt         ← Claude API 호출 구현
├── OpenAiClient.kt            ← GPT API 호출 구현
├── LlmRouter.kt               ← retry/fallback 오케스트레이션
└── AiProperties.kt            ← API 키, 모델명, 한도 등 설정값 (@ConfigurationProperties)
```

이 패키지는 **AI 프로바이더와의 HTTP 통신**만 책임진다. 비즈니스 로직은 없다.

### 6-2. feature/ai/ — AI 관련 비즈니스 로직

```
feature/ai/
├── controller/
│   └── AiController.kt                ← HTTP 매핑 (종목 분석, 포트폴리오 진단)
├── service/
│   ├── StockAiService.kt              ← 종목 분석 비즈니스 로직 + 캐시 관리
│   ├── PortfolioAiService.kt          ← 포트폴리오 진단 비즈니스 로직
│   └── AiUsageService.kt              ← 사용량 조회/차감
├── prompt/
│   └── PromptBuilder.kt               ← 컨텍스트 조립 + 프리셋별 분기
├── scheduler/
│   └── AiCacheCleanupScheduler.kt     ← TTL 초과 캐시 삭제 스케줄러
└── dto/
    ├── AiAnalysisRequest.kt           ← 요청 DTO (stockId, preset, lang)
    └── AiAnalysisResponse.kt          ← 응답 DTO (analysis + disclaimer)
```

### 6-3. domain/entity/ai/ — AI 관련 엔티티

```
domain/entity/ai/
├── StockAiAnalysis.kt         ← 캐시된 AI 분석 결과 (stock_id, date, preset, lang, content)
└── AiUsageLog.kt              ← 사용자별 일일 사용량 (user_id, date, count)
```

### 6-4. domain/repository/ai/ — AI 관련 리포지토리

```
domain/repository/ai/
├── StockAiAnalysisRepository.kt    ← upsert 캐시 조회/저장
└── AiUsageLogRepository.kt         ← atomic increment (INSERT ON CONFLICT UPDATE)
```

---

## 7. 알려진 제한사항과 향후 개선

### 7-1. ConcurrentHashMap → Redis SETNX

현재 Thundering Herd 방지에 `ConcurrentHashMap`을 사용한다.
이는 **단일 서버 인스턴스**에서만 유효하다.

- 서버가 2대 이상이면 각 인스턴스가 독립된 Map을 가지므로 중복 호출 가능
- 서버 재시작 시 Map이 유실되어 일시적으로 중복 호출 발생

**향후:** Redis의 `SETNX` (Set if Not eXists)로 전환하면 다중 인스턴스 환경에서도 동작한다.
현재는 단일 인스턴스 운영이므로 우선순위가 낮다.

### 7-2. OpenAI 폴백 시 응답 톤/길이 일관성

Claude와 GPT는 같은 프롬프트에도 다른 스타일로 응답할 수 있다.

- 문체, 문단 길이, 강조 표현 등이 미묘하게 다름
- 사용자 경험의 일관성이 떨어질 수 있음

**대응:** 배포 후 폴백 빈도와 응답 품질을 모니터링한다. 필요시 프로바이더별 시스템 프롬프트를 미세 조정한다.

### 7-3. "비슷한데 더 안정적인 종목" 프리셋

사용자가 특정 종목의 대안을 요청하는 프리셋 구상이 있다.
구현하려면 동일 섹터 내 타 종목의 지표를 모두 조회하여 LLM에 주입해야 한다.

- 섹터 내 종목 수에 따라 컨텍스트 크기가 크게 증가
- 토큰 비용과 응답 시간 모두 증가
- 어떤 종목을 "비교 대상"으로 선정할지 기준 설계 필요

**결론:** Phase 4 이후에 검토한다. 현재는 섹터 평균과의 비교로 대체한다.

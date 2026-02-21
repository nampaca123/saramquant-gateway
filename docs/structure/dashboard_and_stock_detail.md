# 대시보드 & 종목 상세 설계 문서

> saramquant-gateway의 Dashboard(Screener 통합)와 Stock Detail 기능의 아키텍처 및 설계 결정 기록

---

## 1. 대시보드 (Screener 통합) 설계

### 1-1. 리스크 뱃지 전용 API 제거 → 대시보드 통합

초기에는 `/api/risk-badges` 전용 엔드포인트가 존재했다.
그러나 프론트엔드 화면 분석 결과, 리스크 뱃지가 **독립적으로 쓰이는 화면이 없었다.**
카드 UI에서 종목명·등락률·뱃지·핵심 지표를 한꺼번에 보여줘야 하므로,
별도 API를 호출하면 프론트가 두 번 요청 → 두 번 렌더링하는 비효율이 발생한다.

따라서 `RiskBadgeController`를 삭제하고, 대시보드 응답 안에 뱃지 정보를 포함시켰다.
API 수가 줄어 클라이언트 코드도 단순해진다.

### 1-2. 카드 UI 정보 선택 근거

하나의 카드에 담기는 정보:

| 항목 | 선택 이유 |
|------|-----------|
| **종목명 (ticker + name)** | 식별 필수 |
| **등락률 (changePercent)** | 가장 먼저 보는 수치, 시각적 색상 매핑(빨강/파랑) |
| **리스크 뱃지 (tier)** | 프로젝트 핵심 차별점, 한눈에 위험도 파악 |
| **핵심 지표 3개 (beta, sharpe, volatility)** | 리스크 특성을 가장 함축적으로 보여주는 조합 |

지표를 3개로 제한한 이유는 모바일 카드 폭에서 4개 이상이면 가독성이 급격히 떨어지기 때문이다.
나머지 지표는 종목 상세 페이지에서 확인한다.

### 1-3. Batch Fetch 전략 — N+1 방지

대시보드 한 페이지에 20개 종목이 표시된다고 가정하면, 순진하게 구현하면 종목별로 4회 쿼리가 발생해 총 80회 쿼리가 된다 (N+1 문제).

**해결 방식:**

1. `StockRepository`에서 페이지 단위로 종목 ID 목록을 가져온다
2. 4개 테이블(`Stock`, `RiskBadge`, `StockIndicator`, `DailyPrice`)에 대해 `WHERE stock_id IN (...)` 배치 쿼리 실행 — 총 4~5회 쿼리
3. 결과를 `Map<stockId, Entity>` 형태로 메모리에 올린 뒤 DTO 조립

쿼리 수가 종목 수에 비례하지 않고 **테이블 수에만 비례**하므로, 페이지 크기가 커져도 성능이 안정적이다.

### 1-4. 정렬 옵션 설계

| 정렬 키 | 설명 | 비고 |
|----------|------|------|
| `name_asc` | 종목명 가나다/알파벳 순 | 기본 정렬 |
| `risk_high_first` | 위험 등급 높은 순 | tier 숫자 내림차순 |
| `risk_low_first` | 위험 등급 낮은 순 | tier 숫자 오름차순 |
| `change_desc` | 등락률 높은 순 | 급등주 탐색 |
| `change_asc` | 등락률 낮은 순 | 급락주 탐색 |
| `beta_asc` | 베타 낮은 순 | 방어주 스크리닝 |
| `sharpe_desc` | 샤프 비율 높은 순 | 효율적 종목 탐색 |

정렬은 DB 레벨에서 수행한다.
`risk_high_first` / `risk_low_first`의 경우, RiskBadge 테이블을 JOIN하여 `tier` 컬럼으로 정렬한다.
`change_desc` / `change_asc`는 최근 2거래일 종가를 서브쿼리로 비교하므로 약간의 비용이 있지만,
인덱스가 `(stock_id, date DESC)`에 걸려 있어 실용적 범위에서 충분히 빠르다.

### 1-5. 섹터 필터

- **보조 엔드포인트:** `GET /api/dashboard/sectors` — 현재 등록된 섹터 목록 반환
- **캐시:** 섹터 목록은 자주 바뀌지 않으므로 `@Cacheable`로 메모리 캐시, 하루 1회 갱신
- 대시보드 메인 요청에서 `sector` 파라미터로 필터링 → `WHERE sector = :sector` 조건 추가

### 1-6. 페이지네이션

Spring Data의 `PageRequest`와 `Page<T>`를 그대로 활용한다.

- 클라이언트: `?page=0&size=20&sort=name_asc`
- 서버: `PageRequest.of(page, size)` + 커스텀 정렬 적용
- 응답에 `totalElements`, `totalPages`, `hasNext` 포함 → 프론트에서 무한 스크롤 또는 페이지 버튼 구현 가능

---

## 2. Stock Detail 설계

### 2-1. 단일 거대 응답 반환 정책

Stock Detail 화면에 진입하면 다음 정보가 **모두 동시에** 필요하다:

- header (종목 기본 정보 + 현재가 + 등락률)
- badge (리스크 뱃지 정보)
- indicators (기술적 지표 전체)
- fundamentals (PER, PBR, ROE 등 재무 지표)
- sectorComparison (동일 섹터 평균과 비교)
- factorExposure (팩터 노출도)
- aiAnalysis (AI 해석 — 캐시된 것이 있으면 포함)

이를 6~7개 API로 분리하면 프론트에서 `Promise.all`로 병렬 호출하더라도 네트워크 라운드트립이 증가한다.
모바일 환경에서는 특히 커넥션 수 제한과 핸드셰이크 오버헤드가 크므로,
**하나의 `GET /api/stocks/{stockId}` 요청에 모든 데이터를 담아 반환**하는 것이 효율적이다.

서버 내부에서는 각 데이터를 병렬로 조회(`CompletableFuture`)하여 지연을 최소화한다.

### 2-2. 가격 시계열 (OHLCV)

- 형식: JSON 배열 — `[{ date, open, high, low, close, volume }, ...]`
- 기간 옵션: `PricePeriod` enum → `1M`, `3M`, `6M`, `1Y`
- 데이터 크기: 1년치 약 250거래일 × ~200바이트 ≈ **~50KB** — gzip 적용 시 ~15KB
- 증권사 API의 표준 응답 형식을 따르므로 프론트 차트 라이브러리와 호환성이 높다

### 2-3. 벤치마크 비교 (정규화)

종목 수익률과 벤치마크(코스피 등)를 비교할 때:

1. 시작일의 종가를 100으로 정규화 (기준점 통일)
2. 겹치는 거래일만 사용 — **inner join 정책**
3. 종목이 상장 폐지/거래 정지된 날은 제외하여 왜곡 방지

이 방식은 시작점이 달라도 상대 성과를 직관적으로 비교할 수 있다.
프론트에서 두 선(line chart)을 겹쳐 그리면 된다.

### 2-4. 기존 리포지토리 재활용

Stock Detail을 위해 새로운 리포지토리를 만들지 않고 기존 것에 쿼리를 추가한다:

- `DailyPriceRepository` — 기간별 OHLCV 조회 쿼리 추가
- `StockIndicatorRepository` — 종목 ID 기반 단건 조회
- `StockFundamentalRepository` — 종목 ID 기반 단건 조회
- `RiskBadgeRepository` — 종목 ID 기반 단건 조회

단일 책임 원칙에서 벗어나지 않으면서, 불필요한 리포지토리 증식을 방지한다.

---

## 3. 파일 구조

```
feature/
├── dashboard/
│   ├── controller/
│   │   └── DashboardController.kt      ← HTTP 매핑, 파라미터 바인딩
│   ├── service/
│   │   └── DashboardService.kt          ← 배치 조회, 정렬, DTO 조립
│   └── dto/
│       ├── DashboardCardDto.kt          ← 카드 하나의 응답 DTO
│       └── DashboardPageResponse.kt     ← Page 래핑 응답
│
└── stock/
    ├── controller/
    │   └── StockDetailController.kt     ← HTTP 매핑
    ├── service/
    │   └── StockDetailService.kt        ← 병렬 조회, 정규화 계산
    └── dto/
        ├── StockDetailResponse.kt       ← 거대 응답 DTO
        ├── PriceHistoryDto.kt           ← OHLCV 배열
        └── BenchmarkComparisonDto.kt    ← 정규화된 비교 데이터
```

**단일 책임 원칙 적용:**

- **Controller** — HTTP 매핑만 담당. 비즈니스 로직 없음. 파라미터 검증은 `@Valid` 어노테이션 위임.
- **Service** — 비즈니스 로직 집중. 리포지토리 호출, 데이터 조합, 계산.
- **DTO** — 계층 간 데이터 전송 전용. 엔티티를 직접 노출하지 않음으로써 API 스펙과 DB 스키마를 분리.

---

## 4. 쿼리 전략 상세

### 4-1. Dashboard의 tier 필터 경로 vs 일반 경로

대시보드 요청에 `tier` 파라미터가 있는 경우와 없는 경우 쿼리 경로가 다르다:

- **tier 없음 (일반 경로):** `Stock` 테이블에서 페이지네이션 → 결과 ID로 나머지 배치 조회
- **tier 있음 (필터 경로):** `RiskBadge` 테이블을 기준으로 해당 tier의 `stock_id` 목록을 먼저 추출 → 이 ID 목록으로 `Stock` + 나머지 배치 조회

tier 필터 경로에서는 RiskBadge가 드라이빙 테이블이 되므로 쿼리 순서가 바뀐다.
이렇게 하면 tier별 종목 수가 전체보다 훨씬 적을 때 불필요한 데이터 로딩을 줄일 수 있다.

### 4-2. 가격 변동률 계산

```
changePercent = (오늘 종가 - 어제 종가) / 어제 종가 × 100
```

- "오늘"과 "어제"는 **가장 최근 2거래일**을 의미한다
- 주말/공휴일에는 거래가 없으므로 `DailyPrice` 테이블에서 `ORDER BY date DESC LIMIT 2`로 조회
- 장 마감 전(장중)이면 `close` 대신 아직 값이 없을 수 있다 → calc-server의 배치가 장 마감 후 업데이트하므로, 장중 데이터는 별도 처리 없이 전일 기준으로 표시

### 4-3. SectorAggregate와 FactorExposure — JPA 매핑만 존재하는 이유

`SectorAggregate`와 `FactorExposure`는 새로운 비즈니스 로직이 아니다.
calc-server가 배치로 계산하여 DB에 저장해둔 결과를 **읽기 전용으로 매핑**하는 엔티티이다.

- `SectorAggregate` — 섹터별 평균 지표 (calc-server가 주기적으로 갱신)
- `FactorExposure` — 종목별 팩터 노출도 (Fama-French 등, calc-server가 계산)

Gateway에서는 이 테이블을 SELECT만 하면 되므로, JPA `@Entity` 매핑과 `JpaRepository`만 추가하면 충분하다.
별도의 Service 계층 없이 StockDetailService에서 직접 리포지토리를 호출하여 DTO에 매핑한다.

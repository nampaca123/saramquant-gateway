# Screener 쿼리 라우팅 & 방어 로직

> `DashboardService.list()` → `DashboardQueryRepository.search()` 간의 라우팅 결정과, 데이터 방어 전략을 기록한다.

---

## 1. 쿼리 라우팅

`DashboardService.list(filter)` 는 두 경로로 분기한다.

| 경로 | 조건 | 특징 |
|------|------|------|
| **Full Query** (`queryRepo.search`) | `market == null` 이거나 `needsFullQuery() == true` | Native SQL. 4개 LATERAL JOIN (indicators, fundamentals, prices×2). 정렬·범위·차원 필터 모두 지원. |
| **Simple Path** (`listByTier` / `listByStock`) | 위 조건 불충족 | Spring Data JPA. `name` 정렬 고정. fundamentals 데이터 없음. |

### `needsFullQuery()` 판정 기준

```kotlin
fun needsFullQuery(): Boolean =
    sort !in SIMPLE_SORTS           // name_asc, name_desc 외의 정렬
    || query != null                // 검색어
    || hasDimensionFilters()        // 5대 차원 티어 필터
    || 범위 필터 존재               // betaMin, roeMax 등
```

**설계 의도**: `stock_indicators` / `stock_fundamentals` 컬럼을 참조하는 정렬(beta, sharpe, per, roe 등)은 반드시 Full Query 경로를 타야 해당 테이블이 JOIN된다. `name_asc` / `name_desc`만 Simple Path에서 처리 가능.

---

## 2. NaN 방어

### 배경

PostgreSQL `numeric` 타입은 `NaN`을 허용한다. Java `BigDecimal`은 NaN을 표현할 수 없어, JDBC 드라이버가 `getObject()`로 NaN numeric을 읽으면 **예외가 발생**한다.

### 방어 위치: LATERAL 서브쿼리

```sql
LEFT JOIN LATERAL (
    SELECT stock_id,
           NULLIF(beta, 'NaN')   AS beta,
           NULLIF(sharpe, 'NaN') AS sharpe,
           ...
    FROM stock_indicators WHERE stock_id = s.id ORDER BY date DESC LIMIT 1
) si ON true
```

`NULLIF(col, 'NaN')`은 NaN을 NULL로 변환한다. NULL은 `BigDecimal`에서 `null`로 안전하게 매핑되며, `NULLS LAST` 정렬도 정상 동작한다.

`stock_fundamentals`도 동일 패턴 적용.

### NaN이 문제를 일으키는 이유 상세

| 구분 | NULL | NaN |
|------|------|-----|
| PostgreSQL 정렬 | `NULLS LAST`로 제어 가능 | NULL이 아님. `>` 모든 수. `DESC`에서 1위로 올라옴 |
| JDBC `getObject()` | `null` 반환 | `BigDecimal("NaN")` 시도 → `NumberFormatException` |
| Kotlin `as? BigDecimal` | `null` | 예외 발생 전 단계에서 이미 터짐 |

---

## 3. 정렬 매핑 (SORT_MAP)

| 프론트 값 | SQL ORDER BY | 참조 테이블 |
|-----------|-------------|------------|
| `name_asc/desc` | `s.name` | stocks |
| `beta_asc/desc` | `si.beta` | stock_indicators |
| `sharpe_asc/desc` | `si.sharpe` | stock_indicators |
| `rsi_asc/desc` | `si.rsi_14` | stock_indicators |
| `atr_asc/desc` | `si.atr_14` | stock_indicators |
| `adx_asc/desc` | `si.adx_14` | stock_indicators |
| `per_asc/desc` | `sf.per` | stock_fundamentals |
| `pbr_asc/desc` | `sf.pbr` | stock_fundamentals |
| `roe_asc/desc` | `sf.roe` | stock_fundamentals |
| `debt_ratio_asc/desc` | `sf.debt_ratio` | stock_fundamentals |

모든 indicator/fundamental 정렬은 `NULLS LAST`를 사용하여 데이터 없는 종목이 뒤로 간다.

---

## 4. 캐싱

```kotlin
@Cacheable("screener", key = "#filter.hashCode()")
```

- Caffeine 기반 in-memory 캐시.
- key는 `ScreenerFilter` data class의 `hashCode()` (모든 필드 포함).
- 같은 필터 조합은 캐시 히트. 정렬 변경만으로도 hashCode가 달라져 별도 캐시 엔트리.

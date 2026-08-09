package me.saramquantgateway.feature.dashboard.repository

import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import java.math.BigDecimal

// 스크리너 필터를 DuckDB WHERE 절 + 위치 파라미터로 조립한다.
internal class ScreenerSqlBuilder(private val filter: ScreenerFilter) {

    private val conditions = mutableListOf("s.is_active")
    private val params = mutableListOf<Any>()

    fun marketGroups(): List<String> {
        val markets = filter.markets ?: listOfNotNull(filter.market)
        if (markets.isEmpty()) return ALL_MARKET_GROUPS
        return markets.map { if (it.startsWith("KR")) "KR" else "US" }.distinct()
    }

    fun orderBy(): String = SORT_MAP[filter.sort] ?: SORT_MAP.getValue("name_asc")

    fun build(): Pair<String, List<Any>> {
        filter.markets?.let { inCondition("s.market", it) }
            ?: filter.market?.let { comparison("s.market =", it) }
        filter.tiers?.let { inCondition("rb.summary_tier", it) }
        filter.sectors?.let { inCondition("s.sector", it) }
            ?: filter.sector?.let { comparison("s.sector =", it) }
        filter.excludeStockIds?.takeIf { it.isNotEmpty() }?.let {
            conditions += "s.id NOT IN (${it.joinToString(", ") { "?" }})"
            params.addAll(it)
        }

        range("si.beta", filter.betaMin, filter.betaMax)
        range("si.rsi_14", filter.rsiMin, filter.rsiMax)
        range("si.sharpe", filter.sharpeMin, filter.sharpeMax)
        range("si.atr_14", filter.atrMin, filter.atrMax)
        range("si.adx_14", filter.adxMin, filter.adxMax)
        range("sf.per", filter.perMin, filter.perMax)
        range("sf.pbr", filter.pbrMin, filter.pbrMax)
        range("sf.roe", filter.roeMin, filter.roeMax)
        range("sf.debt_ratio", filter.debtRatioMin, filter.debtRatioMax)

        dimensionTier("price_heat", filter.priceHeatTiers)
        dimensionTier("volatility", filter.volatilityTiers)
        dimensionTier("trend", filter.trendTiers)
        dimensionTier("company_health", filter.companyHealthTiers)
        dimensionTier("valuation", filter.valuationTiers)

        filter.query?.let {
            conditions += "(s.name ILIKE ? OR s.symbol ILIKE ?)"
            params += "%$it%"
            params += "%$it%"
        }

        return "WHERE ${conditions.joinToString(" AND ")}" to params.toList()
    }

    private fun comparison(columnWithOperator: String, value: Any) {
        conditions += "$columnWithOperator ?"
        params += value
    }

    private fun inCondition(column: String, values: List<Any>) {
        if (values.isEmpty()) return
        conditions += "$column IN (${values.joinToString(", ") { "?" }})"
        params.addAll(values)
    }

    private fun range(column: String, min: BigDecimal?, max: BigDecimal?) {
        min?.let { comparison("$column >=", it) }
        max?.let { comparison("$column <=", it) }
    }

    // dims 배열에서 name이 일치하는 원소의 tier를 뽑아 비교한다(기존 jsonb EXISTS와 동등).
    private fun dimensionTier(dimension: String, tiers: List<String>?) {
        if (tiers.isNullOrEmpty()) return
        conditions += """
            list_extract(
                json_extract_string(rb.dimensions, '$.dims[*].tier'),
                list_position(json_extract_string(rb.dimensions, '$.dims[*].name'), ?)
            ) IN (${tiers.joinToString(", ") { "?" }})
        """.trimIndent()
        params += dimension
        params.addAll(tiers)
    }

    private companion object {
        val ALL_MARKET_GROUPS = listOf("KR", "US")

        // base CTE의 출력 컬럼명 기준 정렬식 — id 타이브레이크로 페이지 경계를 안정화한다.
        val SORT_MAP = mapOf(
            "name_asc" to "name ASC",
            "name_desc" to "name DESC",
            "beta_asc" to "beta ASC NULLS LAST",
            "beta_desc" to "beta DESC NULLS LAST",
            "sharpe_asc" to "sharpe ASC NULLS LAST",
            "sharpe_desc" to "sharpe DESC NULLS LAST",
            "rsi_asc" to "rsi_14 ASC NULLS LAST",
            "rsi_desc" to "rsi_14 DESC NULLS LAST",
            "atr_asc" to "atr_14 ASC NULLS LAST",
            "atr_desc" to "atr_14 DESC NULLS LAST",
            "adx_asc" to "adx_14 ASC NULLS LAST",
            "adx_desc" to "adx_14 DESC NULLS LAST",
            "per_asc" to "per ASC NULLS LAST",
            "per_desc" to "per DESC NULLS LAST",
            "pbr_asc" to "pbr ASC NULLS LAST",
            "pbr_desc" to "pbr DESC NULLS LAST",
            "roe_asc" to "roe ASC NULLS LAST",
            "roe_desc" to "roe DESC NULLS LAST",
            "debt_ratio_asc" to "debt_ratio ASC NULLS LAST",
            "debt_ratio_desc" to "debt_ratio DESC NULLS LAST",
        ).mapValues { (_, order) -> "$order, id" }
    }
}

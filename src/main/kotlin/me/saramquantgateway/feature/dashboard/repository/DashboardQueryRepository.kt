package me.saramquantgateway.feature.dashboard.repository

import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.feature.dashboard.dto.DataFreshnessResponse
import me.saramquantgateway.feature.dashboard.dto.DashboardPage
import me.saramquantgateway.feature.dashboard.dto.DashboardStockItem
import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import java.sql.Timestamp
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode

@Repository
class DashboardQueryRepository(
    private val em: EntityManager,
    private val objectMapper: ObjectMapper,
) {

    companion object {
        private val SORT_MAP = mapOf(
            "name_asc" to "s.name ASC",
            "name_desc" to "s.name DESC",
            "beta_asc" to "si.beta ASC NULLS LAST",
            "beta_desc" to "si.beta DESC NULLS LAST",
            "sharpe_asc" to "si.sharpe ASC NULLS LAST",
            "sharpe_desc" to "si.sharpe DESC NULLS LAST",
            "rsi_asc" to "si.rsi_14 ASC NULLS LAST",
            "rsi_desc" to "si.rsi_14 DESC NULLS LAST",
            "atr_asc" to "si.atr_14 ASC NULLS LAST",
            "atr_desc" to "si.atr_14 DESC NULLS LAST",
            "adx_asc" to "si.adx_14 ASC NULLS LAST",
            "adx_desc" to "si.adx_14 DESC NULLS LAST",
            "per_asc" to "sf.per ASC NULLS LAST",
            "per_desc" to "sf.per DESC NULLS LAST",
            "pbr_asc" to "sf.pbr ASC NULLS LAST",
            "pbr_desc" to "sf.pbr DESC NULLS LAST",
            "roe_asc" to "sf.roe ASC NULLS LAST",
            "roe_desc" to "sf.roe DESC NULLS LAST",
            "debt_ratio_asc" to "sf.debt_ratio ASC NULLS LAST",
            "debt_ratio_desc" to "sf.debt_ratio DESC NULLS LAST",
        )
    }

    fun search(filter: ScreenerFilter): DashboardPage {
        val (whereSql, params) = buildWhere(filter)
        val orderBy = SORT_MAP[filter.sort] ?: SORT_MAP.getValue("name_asc")

        val baseSql = """
            FROM stocks s
            JOIN risk_badges rb ON rb.stock_id = s.id
            LEFT JOIN LATERAL (
                SELECT stock_id, beta, rsi_14, sharpe, atr_14, adx_14
                FROM stock_indicators WHERE stock_id = s.id ORDER BY date DESC LIMIT 1
            ) si ON true
            LEFT JOIN LATERAL (
                SELECT stock_id, per, pbr, roe, debt_ratio
                FROM stock_fundamentals WHERE stock_id = s.id ORDER BY date DESC LIMIT 1
            ) sf ON true
            LEFT JOIN LATERAL (
                SELECT close, date FROM daily_prices WHERE stock_id = s.id ORDER BY date DESC LIMIT 1
            ) dp ON true
            LEFT JOIN LATERAL (
                SELECT close, date FROM daily_prices WHERE stock_id = s.id ORDER BY date DESC LIMIT 1 OFFSET 1
            ) dp_prev ON true
            $whereSql
        """.trimIndent()

        val countQuery = em.createNativeQuery("SELECT COUNT(*) $baseSql")
        params.forEach { (k, v) -> countQuery.setParameter(k, v) }
        val totalCount = (countQuery.singleResult as Number).toLong()

        if (totalCount == 0L) return emptyPage(filter)

        val dataSql = """
            SELECT s.id, s.symbol, s.name, s.market::text, s.sector,
                   rb.summary_tier, rb.dimensions::text,
                   si.beta, si.rsi_14, si.sharpe, si.atr_14, si.adx_14,
                   sf.per, sf.pbr, sf.roe, sf.debt_ratio,
                   dp.close, dp.date, dp_prev.close, dp_prev.date
            $baseSql
            ORDER BY $orderBy
            LIMIT :pageSize OFFSET :pageOffset
        """.trimIndent()

        val dataQuery = em.createNativeQuery(dataSql)
        params.forEach { (k, v) -> dataQuery.setParameter(k, v) }
        dataQuery.setParameter("pageSize", filter.size)
        dataQuery.setParameter("pageOffset", filter.page * filter.size)

        @Suppress("UNCHECKED_CAST")
        val rows = dataQuery.resultList as List<Array<Any?>>
        val items = rows.map(::mapRow)

        val totalPages = ((totalCount + filter.size - 1) / filter.size).toInt()
        return DashboardPage(
            content = items,
            totalElements = totalCount,
            totalPages = totalPages,
            number = filter.page,
            size = filter.size,
            hasNext = filter.page < totalPages - 1,
        )
    }

    private fun buildWhere(filter: ScreenerFilter): Pair<String, MutableMap<String, Any>> {
        val conditions = mutableListOf("s.is_active = true")
        val params = mutableMapOf<String, Any>()
        filter.market?.let {
            conditions += "s.market::text = :market"
            params["market"] = it
        }

        filter.tiers?.let { tiers ->
            val placeholders = tiers.indices.joinToString(", ") { ":tier$it" }
            conditions += "rb.summary_tier IN ($placeholders)"
            tiers.forEachIndexed { i, t -> params["tier$i"] = t }
        }
        filter.sector?.let {
            conditions += "s.sector = :sector"
            params["sector"] = it
        }

        addRange(conditions, params, "si.beta", "betaMin", "betaMax", filter.betaMin, filter.betaMax)
        addRange(conditions, params, "si.rsi_14", "rsiMin", "rsiMax", filter.rsiMin, filter.rsiMax)
        addRange(conditions, params, "si.sharpe", "sharpeMin", "sharpeMax", filter.sharpeMin, filter.sharpeMax)
        addRange(conditions, params, "si.atr_14", "atrMin", "atrMax", filter.atrMin, filter.atrMax)
        addRange(conditions, params, "si.adx_14", "adxMin", "adxMax", filter.adxMin, filter.adxMax)
        addRange(conditions, params, "sf.per", "perMin", "perMax", filter.perMin, filter.perMax)
        addRange(conditions, params, "sf.pbr", "pbrMin", "pbrMax", filter.pbrMin, filter.pbrMax)
        addRange(conditions, params, "sf.roe", "roeMin", "roeMax", filter.roeMin, filter.roeMax)
        addRange(conditions, params, "sf.debt_ratio", "drMin", "drMax", filter.debtRatioMin, filter.debtRatioMax)

        filter.query?.let { q ->
            conditions += "(s.name ILIKE :query OR s.symbol ILIKE :query)"
            params["query"] = "%$q%"
        }

        return "WHERE ${conditions.joinToString(" AND ")}" to params
    }

    private fun addRange(
        conditions: MutableList<String>, params: MutableMap<String, Any>,
        column: String, minKey: String, maxKey: String,
        min: BigDecimal?, max: BigDecimal?,
    ) {
        min?.let { conditions += "$column >= :$minKey"; params[minKey] = it }
        max?.let { conditions += "$column <= :$maxKey"; params[maxKey] = it }
    }

    private fun mapRow(row: Array<Any?>): DashboardStockItem {
        val latestClose = row[16] as? BigDecimal
        val prevClose = row[18] as? BigDecimal
        val changePct = if (latestClose != null && prevClose != null && prevClose.signum() != 0)
            latestClose.subtract(prevClose)
                .multiply(BigDecimal(100))
                .divide(prevClose, 2, RoundingMode.HALF_UP)
                .toDouble()
        else null

        return DashboardStockItem(
            stockId = (row[0] as Number).toLong(),
            symbol = row[1] as String,
            name = row[2] as String,
            market = row[3] as String,
            sector = row[4] as? String,
            latestClose = latestClose,
            priceChangePercent = changePct,
            comparedDate = row[19]?.toString(),
            summaryTier = row[5] as? String,
            dimensionTiers = (row[6] as? String)?.let(::parseDimensionTiers),
            beta = row[7] as? BigDecimal,
            rsi14 = row[8] as? BigDecimal,
            sharpe = row[9] as? BigDecimal,
            atr14 = row[10] as? BigDecimal,
            adx14 = row[11] as? BigDecimal,
            per = row[12] as? BigDecimal,
            pbr = row[13] as? BigDecimal,
            roe = row[14] as? BigDecimal,
            debtRatio = row[15] as? BigDecimal,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseDimensionTiers(json: String): Map<String, String>? = try {
        val root = objectMapper.readValue(json, Map::class.java) as Map<String, Any>
        val list = root["dims"] as? List<Map<String, Any>> ?: emptyList()
        list.mapNotNull { d ->
            val name = d["name"]?.toString() ?: return@mapNotNull null
            val tier = d["tier"]?.toString() ?: return@mapNotNull null
            name to tier
        }.toMap().ifEmpty { null }
    } catch (_: Exception) {
        null
    }

    fun dataFreshness(): DataFreshnessResponse {
        val sql = """
            SELECT
              (SELECT dp.date::text FROM daily_prices dp JOIN stocks s ON s.id = dp.stock_id
               WHERE s.market IN ('KR_KOSPI','KR_KOSDAQ') ORDER BY dp.date DESC LIMIT 1),
              (SELECT dp.created_at FROM daily_prices dp JOIN stocks s ON s.id = dp.stock_id
               WHERE s.market IN ('KR_KOSPI','KR_KOSDAQ') ORDER BY dp.date DESC LIMIT 1),
              (SELECT dp.date::text FROM daily_prices dp JOIN stocks s ON s.id = dp.stock_id
               WHERE s.market IN ('US_NYSE','US_NASDAQ') ORDER BY dp.date DESC LIMIT 1),
              (SELECT dp.created_at FROM daily_prices dp JOIN stocks s ON s.id = dp.stock_id
               WHERE s.market IN ('US_NYSE','US_NASDAQ') ORDER BY dp.date DESC LIMIT 1),
              (SELECT MAX(fs.created_at) FROM financial_statements fs JOIN stocks s ON s.id = fs.stock_id
               WHERE s.market IN ('KR_KOSPI','KR_KOSDAQ')),
              (SELECT MAX(fs.created_at) FROM financial_statements fs JOIN stocks s ON s.id = fs.stock_id
               WHERE s.market IN ('US_NYSE','US_NASDAQ'))
        """.trimIndent()

        val row = em.createNativeQuery(sql).singleResult as Array<*>
        return DataFreshnessResponse(
            krPriceDate = row[0] as? String,
            krPriceCollectedAt = (row[1] as? Timestamp)?.toInstant()?.toString(),
            usPriceDate = row[2] as? String,
            usPriceCollectedAt = (row[3] as? Timestamp)?.toInstant()?.toString(),
            krFinancialCollectedAt = (row[4] as? Timestamp)?.toInstant()?.toString(),
            usFinancialCollectedAt = (row[5] as? Timestamp)?.toInstant()?.toString(),
        )
    }

    private fun emptyPage(filter: ScreenerFilter) = DashboardPage(
        content = emptyList(), totalElements = 0, totalPages = 0,
        number = filter.page, size = filter.size, hasNext = false,
    )
}

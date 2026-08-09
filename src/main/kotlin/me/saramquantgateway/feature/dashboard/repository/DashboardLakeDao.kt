package me.saramquantgateway.feature.dashboard.repository

import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.lake.recentDatesFrom
import me.saramquantgateway.feature.dashboard.dto.DashboardPage
import me.saramquantgateway.feature.dashboard.dto.DashboardStockItem
import me.saramquantgateway.feature.dashboard.dto.DataFreshnessResponse
import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import me.saramquantgateway.feature.dashboard.dto.StockSearchResult
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import me.saramquantgateway.infra.storage.s3.RunSummaryReader
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.ResultSet
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Repository
class DashboardLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
    private val runSummaryReader: RunSummaryReader,
    private val objectMapper: ObjectMapper,
) {

    // count와 페이지를 두 번 스캔하지 않도록 윈도우 집계로 총건수를 같이 뽑는다.
    fun search(filter: ScreenerFilter): DashboardPage {
        val builder = ScreenerSqlBuilder(filter)
        val (whereSql, whereParams) = builder.build()
        val groups = builder.marketGroups()
        val cteParams = groups + groups
        val baseSql = baseSql(groups) + "\n" + whereSql + "\n)"

        val offset = filter.page.toLong() * filter.size
        val rows = executor.query(
            "$baseSql SELECT *, count(*) OVER () AS total_count FROM base " +
                "ORDER BY ${builder.orderBy()} LIMIT ? OFFSET ?",
            cteParams + whereParams + listOf(filter.size, offset),
        ) { it.getLong("total_count") to mapItem(it) }

        val total = rows.firstOrNull()?.first ?: countMatches(baseSql, cteParams + whereParams)
        if (total == 0L) return emptyPage(filter)
        val items = rows.map { it.second }

        val totalPages = ((total + filter.size - 1) / filter.size).toInt()
        return DashboardPage(
            content = items,
            totalElements = total,
            totalPages = totalPages,
            number = filter.page,
            size = filter.size,
            hasNext = filter.page < totalPages - 1,
        )
    }

    fun searchStocks(query: String, market: Market?, limit: Int): List<StockSearchResult> {
        val prefix = "${query.lowercase()}%"
        val params = mutableListOf<Any>()
        val conditions = mutableListOf("is_active")
        market?.let { conditions += "market = ?"; params += it.name }
        if (query.length == 1) {
            conditions += "lower(symbol) LIKE ?"
            params += prefix
        } else {
            conditions += "(name ILIKE ? OR symbol ILIKE ?)"
            params += "%$query%"
            params += "%$query%"
        }
        params += prefix
        params += limit

        return executor.query(
            """
            SELECT id, symbol, name, market, sector FROM ${resolver.ref("stocks")}
            WHERE ${conditions.joinToString(" AND ")}
            ORDER BY CASE WHEN lower(symbol) LIKE ? THEN 0 ELSE 1 END, name
            LIMIT ?
            """.trimIndent(),
            params,
        ) { rs ->
            StockSearchResult(
                stockId = rs.getLong("id"),
                symbol = rs.getString("symbol"),
                name = rs.getString("name"),
                market = rs.getString("market"),
                sector = rs.getString("sector"),
            )
        }
    }

    // 가격은 calc_kr/calc_us, 재무는 분기 배치인 calc_*-fs 런 요약을 각각 출처로 쓴다.
    fun dataFreshness(): DataFreshnessResponse = DataFreshnessResponse(
        krPriceUpdatedAt = runTimestamp("calc_kr"),
        usPriceUpdatedAt = runTimestamp("calc_us"),
        krFinancialUpdatedAt = runTimestamp("calc_kr-fs") ?: runTimestamp("calc_kr"),
        usFinancialUpdatedAt = runTimestamp("calc_us-fs") ?: runTimestamp("calc_us"),
    )

    private fun countMatches(baseSql: String, params: List<Any>): Long =
        executor.query("$baseSql SELECT count(*) AS c FROM base", params) { it.getLong("c") }.single()

    // 마이그레이션 이전 Postgres timestamptz::text 표기(2026-08-09 12:00:00+00)를 그대로 유지한다.
    private fun runTimestamp(command: String): String? =
        runSummaryReader.writtenAtUtc(command)?.let { raw ->
            runCatching { PG_TIMESTAMP_FMT.format(Instant.parse(raw)) }.getOrDefault(raw)
        }

    private fun baseSql(groups: List<String>): String {
        val priceRef = resolver.ref("daily_prices")
        val fundamentalRef = resolver.ref("stock_fundamentals")
        val groupPlaceholders = groups.joinToString(", ") { "?" }
        return """
        WITH sf AS (
            SELECT stock_id, per, pbr, roe, debt_ratio FROM (
                SELECT stock_id, per, pbr, roe, debt_ratio,
                       row_number() OVER (PARTITION BY stock_id ORDER BY date DESC) AS rn
                FROM $fundamentalRef WHERE date >= ${recentDatesFrom(fundamentalRef)}
            ) t WHERE rn = 1
        ),
        si AS (
            SELECT stock_id, beta, rsi_14, sharpe, atr_14, adx_14 FROM (
                SELECT stock_id, beta, rsi_14, sharpe, atr_14, adx_14,
                       row_number() OVER (PARTITION BY stock_id ORDER BY date DESC) AS rn
                FROM ${resolver.ref("stock_indicators")}
            ) t WHERE rn = 1
        ),
        dp AS (
            SELECT stock_id, close, date, rn FROM (
                SELECT stock_id, close, date,
                       row_number() OVER (PARTITION BY stock_id ORDER BY date DESC) AS rn
                FROM $priceRef
                WHERE market IN ($groupPlaceholders)
                  AND date >= ${recentDatesFrom(priceRef, " WHERE market IN ($groupPlaceholders)")}
            ) t WHERE rn <= 2
        ),
        base AS (
            SELECT s.id, s.symbol, s.name, s.market, s.sector,
                   rb.summary_tier, rb.dimensions,
                   si.beta, si.rsi_14, si.sharpe, si.atr_14, si.adx_14,
                   sf.per, sf.pbr, sf.roe, sf.debt_ratio,
                   cur.close AS latest_close, prv.close AS prev_close, prv.date AS compared_date
            FROM ${resolver.ref("stocks")} s
            LEFT JOIN ${resolver.ref("risk_badges")} rb ON rb.stock_id = s.id
            LEFT JOIN si ON si.stock_id = s.id
            LEFT JOIN sf ON sf.stock_id = s.id
            LEFT JOIN dp cur ON cur.stock_id = s.id AND cur.rn = 1
            LEFT JOIN dp prv ON prv.stock_id = s.id AND prv.rn = 2
        """.trimIndent()
    }

    private fun mapItem(rs: ResultSet): DashboardStockItem {
        val latestClose = rs.getBigDecimal("latest_close")
        val prevClose = rs.getBigDecimal("prev_close")
        val changePct = if (latestClose != null && prevClose != null && prevClose.signum() != 0)
            latestClose.subtract(prevClose)
                .multiply(BigDecimal(100))
                .divide(prevClose, 2, RoundingMode.HALF_UP)
                .toDouble()
        else null

        return DashboardStockItem(
            stockId = rs.getLong("id"),
            symbol = rs.getString("symbol"),
            name = rs.getString("name"),
            market = rs.getString("market"),
            sector = rs.getString("sector"),
            latestClose = latestClose,
            priceChangePercent = changePct,
            comparedDate = rs.getDate("compared_date")?.toLocalDate()?.toString(),
            summaryTier = rs.getString("summary_tier"),
            dimensionTiers = rs.getString("dimensions")?.let(::parseDimensionTiers),
            beta = rs.getBigDecimal("beta"),
            rsi14 = rs.getBigDecimal("rsi_14"),
            sharpe = rs.getBigDecimal("sharpe"),
            atr14 = rs.getBigDecimal("atr_14"),
            adx14 = rs.getBigDecimal("adx_14"),
            per = rs.getBigDecimal("per"),
            pbr = rs.getBigDecimal("pbr"),
            roe = rs.getBigDecimal("roe"),
            debtRatio = rs.getBigDecimal("debt_ratio"),
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

    private fun emptyPage(filter: ScreenerFilter) = DashboardPage(
        content = emptyList(), totalElements = 0, totalPages = 0,
        number = filter.page, size = filter.size, hasNext = false,
    )

    private companion object {
        val PG_TIMESTAMP_FMT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss'+00'").withZone(ZoneOffset.UTC)
    }
}

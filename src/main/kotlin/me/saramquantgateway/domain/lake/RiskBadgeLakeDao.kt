package me.saramquantgateway.domain.lake

import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.domain.entity.riskbadge.RiskBadge
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.store.PageResult
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.springframework.stereotype.Component
import java.sql.ResultSet

data class MarketTierCountRow(val market: String, val summaryTier: String, val count: Long)

// risk_badges는 종목당 1행 스냅샷 테이블이다.
@Component
class RiskBadgeLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
    private val objectMapper: ObjectMapper,
) {

    fun findByStockId(stockId: Long): RiskBadge? = executor.query(
        "SELECT $COLUMNS FROM ${ref()} WHERE stock_id = ?",
        listOf(stockId),
        ::mapBadge,
    ).firstOrNull()

    fun findByStockIds(stockIds: List<Long>): List<RiskBadge> {
        if (stockIds.isEmpty()) return emptyList()
        return executor.query(
            "SELECT $COLUMNS FROM ${ref()} WHERE stock_id IN (${placeholders(stockIds.size)})",
            stockIds,
            ::mapBadge,
        )
    }

    fun findPageByMarketAndTiers(
        market: Market,
        summaryTiers: List<String>,
        page: Int,
        size: Int,
    ): PageResult<RiskBadge> {
        if (summaryTiers.isEmpty()) return PageResult(emptyList(), 0, false)
        val where = "WHERE market = ? AND summary_tier IN (${placeholders(summaryTiers.size)})"
        val params = listOf<Any>(market.name) + summaryTiers

        val total = executor.query("SELECT count(*) AS c FROM ${ref()} $where", params) { it.getLong("c") }.single()
        if (total == 0L) return PageResult(emptyList(), 0, false)

        val offset = page.toLong() * size
        val content = executor.query(
            "SELECT $COLUMNS FROM ${ref()} $where ORDER BY stock_id LIMIT ? OFFSET ?",
            params + listOf(size, offset),
            ::mapBadge,
        )
        return PageResult(content, total, offset + content.size < total)
    }

    fun countByMarketAndTier(): List<MarketTierCountRow> = executor.query(
        "SELECT market, summary_tier, count(*) AS c FROM ${ref()} GROUP BY market, summary_tier",
    ) { rs -> MarketTierCountRow(rs.getString("market"), rs.getString("summary_tier"), rs.getLong("c")) }

    private fun ref() = resolver.ref(TABLE)

    private fun mapBadge(rs: ResultSet) = RiskBadge(
        stockId = rs.getLong("stock_id"),
        market = Market.valueOf(rs.getString("market")),
        date = rs.localDate("date"),
        summaryTier = rs.getString("summary_tier"),
        dimensions = parseDimensions(rs.getString("dimensions")),
    )

    @Suppress("UNCHECKED_CAST")
    private fun parseDimensions(json: String?): Map<String, Any> =
        if (json.isNullOrBlank()) emptyMap()
        else runCatching { objectMapper.readValue(json, Map::class.java) as Map<String, Any> }.getOrDefault(emptyMap())

    private companion object {
        const val TABLE = "risk_badges"
        const val COLUMNS = "stock_id, market, date, summary_tier, dimensions"
    }
}

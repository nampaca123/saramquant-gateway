package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.store.PageResult
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.springframework.stereotype.Component
import java.sql.ResultSet

@Component
class StockLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
) {

    fun findBySymbolAndMarket(symbol: String, market: Market): Stock? = executor.query(
        "SELECT $COLUMNS FROM ${ref()} WHERE symbol = ? AND market = ? AND is_active ORDER BY id LIMIT 1",
        listOf(symbol, market.name),
        ::mapStock,
    ).firstOrNull()

    fun findById(id: Long): Stock? = executor.query(
        "SELECT $COLUMNS FROM ${ref()} WHERE id = ?",
        listOf(id),
        ::mapStock,
    ).firstOrNull()

    fun findByIds(ids: List<Long>): List<Stock> {
        if (ids.isEmpty()) return emptyList()
        return executor.query(
            "SELECT $COLUMNS FROM ${ref()} WHERE id IN (${placeholders(ids.size)})",
            ids,
            ::mapStock,
        )
    }

    fun findPageByMarket(market: Market, sector: String?, page: Int, size: Int): PageResult<Stock> {
        val params = mutableListOf<Any>(market.name)
        var where = "WHERE market = ? AND is_active"
        if (sector != null) {
            where += " AND sector = ?"
            params += sector
        }

        val total = executor.query("SELECT count(*) AS c FROM ${ref()} $where", params) { it.getLong("c") }.single()
        if (total == 0L) return PageResult(emptyList(), 0, false)

        val offset = page.toLong() * size
        val content = executor.query(
            "SELECT $COLUMNS FROM ${ref()} $where ORDER BY name, id LIMIT ? OFFSET ?",
            params + listOf(size, offset),
            ::mapStock,
        )
        return PageResult(content, total, offset + content.size < total)
    }

    fun findDistinctSectors(market: Market?): List<String> {
        val where = if (market != null) "WHERE is_active AND sector IS NOT NULL AND market = ?" else
            "WHERE is_active AND sector IS NOT NULL"
        return executor.query(
            "SELECT DISTINCT sector FROM ${ref()} $where ORDER BY sector",
            listOfNotNull(market?.name),
        ) { it.getString("sector") }
    }

    private fun ref() = resolver.ref(TABLE)

    private companion object {
        const val TABLE = "stocks"
        const val COLUMNS = "id, symbol, name, market, is_active, sector, dart_corp_code"

        fun mapStock(rs: ResultSet) = Stock(
            id = rs.getLong("id"),
            symbol = rs.getString("symbol"),
            name = rs.getString("name"),
            market = Market.valueOf(rs.getString("market")),
            isActive = rs.getBoolean("is_active"),
            sector = rs.getString("sector"),
            dartCorpCode = rs.getString("dart_corp_code"),
        )
    }
}

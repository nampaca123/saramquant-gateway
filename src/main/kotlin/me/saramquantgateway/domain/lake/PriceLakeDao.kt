package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.entity.market.BenchmarkDailyPrice
import me.saramquantgateway.domain.entity.stock.DailyPrice
import me.saramquantgateway.domain.enum.market.Benchmark
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.springframework.stereotype.Component
import java.sql.ResultSet
import java.time.LocalDate

@Component
class PriceLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
) {

    fun findByStockIdAndDateBetween(
        stockId: Long,
        market: Market,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<DailyPrice> = executor.query(
        """
        SELECT $PRICE_COLUMNS FROM ${dailyRef()}
        WHERE market = ? AND stock_id = ? AND date BETWEEN ? AND ?
        ORDER BY date DESC
        """.trimIndent(),
        listOf(marketGroupOf(market), stockId, startDate, endDate),
        ::mapDailyPrice,
    )

    fun findTop2ByStockId(stockId: Long, market: Market): List<DailyPrice> = executor.query(
        """
        SELECT $PRICE_COLUMNS FROM ${dailyRef()}
        WHERE market = ? AND stock_id = ? AND date >= ?
        ORDER BY date DESC LIMIT 2
        """.trimIndent(),
        listOf(marketGroupOf(market), stockId, latestLookbackFrom()),
        ::mapDailyPrice,
    )

    fun findTop2PerStock(stockIds: List<Long>, marketGroup: String): List<DailyPrice> {
        if (stockIds.isEmpty()) return emptyList()
        return executor.query(
            """
            SELECT $PRICE_COLUMNS FROM (
                SELECT stock_id, date, open, high, low, close, volume,
                       row_number() OVER (PARTITION BY stock_id ORDER BY date DESC) AS rn
                FROM ${dailyRef()}
                WHERE market = ? AND date >= ? AND stock_id IN (${placeholders(stockIds.size)})
            ) t WHERE rn <= 2
            """.trimIndent(),
            listOf(marketGroup, latestLookbackFrom()) + stockIds,
            ::mapDailyPrice,
        )
    }

    fun findBenchmarkByDateBetween(
        benchmark: Benchmark,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<BenchmarkDailyPrice> = executor.query(
        """
        SELECT benchmark, date, close FROM ${benchmarkRef()}
        WHERE benchmark = ? AND date BETWEEN ? AND ?
        ORDER BY date DESC
        """.trimIndent(),
        listOf(benchmark.name, startDate, endDate),
        ::mapBenchmarkPrice,
    )

    fun findTop2Benchmark(benchmark: Benchmark): List<BenchmarkDailyPrice> = executor.query(
        "SELECT benchmark, date, close FROM ${benchmarkRef()} WHERE benchmark = ? ORDER BY date DESC LIMIT 2",
        listOf(benchmark.name),
        ::mapBenchmarkPrice,
    )

    private fun dailyRef() = resolver.ref(DAILY_TABLE)

    private fun benchmarkRef() = resolver.ref(BENCHMARK_TABLE)

    private companion object {
        const val DAILY_TABLE = "daily_prices"
        const val BENCHMARK_TABLE = "benchmark_daily_prices"
        const val PRICE_COLUMNS = "stock_id, date, open, high, low, close, volume"

        fun mapDailyPrice(rs: ResultSet) = DailyPrice(
            stockId = rs.getLong("stock_id"),
            date = rs.localDate("date"),
            open = rs.getBigDecimal("open"),
            high = rs.getBigDecimal("high"),
            low = rs.getBigDecimal("low"),
            close = rs.getBigDecimal("close"),
            volume = rs.getLong("volume"),
        )

        fun mapBenchmarkPrice(rs: ResultSet) = BenchmarkDailyPrice(
            benchmark = Benchmark.valueOf(rs.getString("benchmark")),
            date = rs.localDate("date"),
            close = rs.getBigDecimal("close"),
        )
    }
}

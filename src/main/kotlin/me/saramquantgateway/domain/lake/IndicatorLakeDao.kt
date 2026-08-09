package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.entity.indicator.StockIndicator
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.duckdb.LakeTableResolver
import org.springframework.stereotype.Component
import java.sql.ResultSet

// stock_indicators는 최신 스냅샷만 유지되는 테이블이라 날짜 프루닝이 필요 없다.
@Component
class IndicatorLakeDao(
    private val executor: DuckDbQueryExecutor,
    private val resolver: LakeTableResolver,
) {

    fun findLatestByStockId(stockId: Long): StockIndicator? = executor.query(
        "SELECT $COLUMNS FROM ${ref()} WHERE stock_id = ? ORDER BY date DESC LIMIT 1",
        listOf(stockId),
        ::mapIndicator,
    ).firstOrNull()

    fun findLatestByStockIds(stockIds: List<Long>): List<StockIndicator> {
        if (stockIds.isEmpty()) return emptyList()
        return executor.query(
            "SELECT $COLUMNS FROM ${ref()} WHERE stock_id IN (${placeholders(stockIds.size)})",
            stockIds,
            ::mapIndicator,
        )
    }

    private fun ref() = resolver.ref(TABLE)

    private companion object {
        const val TABLE = "stock_indicators"
        const val COLUMNS = """
            stock_id, date, sma_20, ema_20, wma_20, rsi_14, macd, macd_signal, macd_hist,
            stoch_k, stoch_d, bb_upper, bb_middle, bb_lower, atr_14, adx_14, plus_di, minus_di,
            obv, vma_20, sar, beta, alpha, sharpe
        """

        fun mapIndicator(rs: ResultSet) = StockIndicator(
            stockId = rs.getLong("stock_id"),
            date = rs.localDate("date"),
            sma20 = rs.decimal("sma_20"),
            ema20 = rs.decimal("ema_20"),
            wma20 = rs.decimal("wma_20"),
            rsi14 = rs.decimal("rsi_14"),
            macd = rs.decimal("macd"),
            macdSignal = rs.decimal("macd_signal"),
            macdHist = rs.decimal("macd_hist"),
            stochK = rs.decimal("stoch_k"),
            stochD = rs.decimal("stoch_d"),
            bbUpper = rs.decimal("bb_upper"),
            bbMiddle = rs.decimal("bb_middle"),
            bbLower = rs.decimal("bb_lower"),
            atr14 = rs.decimal("atr_14"),
            adx14 = rs.decimal("adx_14"),
            plusDi = rs.decimal("plus_di"),
            minusDi = rs.decimal("minus_di"),
            obv = rs.longOrNull("obv"),
            vma20 = rs.longOrNull("vma_20"),
            sar = rs.decimal("sar"),
            beta = rs.decimal("beta"),
            alpha = rs.decimal("alpha"),
            sharpe = rs.decimal("sharpe"),
        )
    }
}

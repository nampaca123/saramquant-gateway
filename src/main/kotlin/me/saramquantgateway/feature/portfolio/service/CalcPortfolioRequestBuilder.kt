package me.saramquantgateway.feature.portfolio.service

import me.saramquantgateway.domain.document.HoldingEntry
import me.saramquantgateway.domain.lake.StockLakeDao
import org.springframework.stereotype.Component

// calc 서버가 요구하는 symbol/market 기반 보유종목 바디를 만든다.
@Component
class CalcPortfolioRequestBuilder(private val stockDao: StockLakeDao) {

    fun build(marketGroup: String, holdings: List<HoldingEntry>): Map<String, Any?> {
        val stockMap = stockDao.findByIds(holdings.map { it.stockId }).associateBy { it.id }
        val rows = holdings.mapNotNull { h ->
            val stock = stockMap[h.stockId] ?: return@mapNotNull null
            mapOf(
                "symbol" to stock.symbol,
                "market" to stock.market.name,
                "shares" to h.shares,
                "avg_price" to h.avgPrice,
                "currency" to h.currency,
                "purchased_at" to h.purchasedAt.toString(),
                "purchase_fx_rate" to h.purchaseFxRate,
            )
        }
        return mapOf("market_group" to marketGroup, "holdings" to rows)
    }
}

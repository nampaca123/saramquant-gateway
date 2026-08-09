package me.saramquantgateway.feature.dashboard.service

import me.saramquantgateway.domain.entity.indicator.StockIndicator
import me.saramquantgateway.domain.entity.riskbadge.RiskBadge
import me.saramquantgateway.domain.entity.stock.DailyPrice
import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.lake.IndicatorLakeDao
import me.saramquantgateway.domain.lake.PriceLakeDao
import me.saramquantgateway.domain.lake.RiskBadgeLakeDao
import me.saramquantgateway.domain.lake.StockLakeDao
import me.saramquantgateway.domain.lake.marketGroupOf
import me.saramquantgateway.feature.dashboard.dto.DataFreshnessResponse
import me.saramquantgateway.feature.dashboard.dto.DashboardPage
import me.saramquantgateway.feature.dashboard.dto.DashboardStockItem
import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import me.saramquantgateway.feature.dashboard.dto.StockSearchResult
import me.saramquantgateway.feature.dashboard.repository.DashboardLakeDao
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class DashboardService(
    private val stockDao: StockLakeDao,
    private val badgeDao: RiskBadgeLakeDao,
    private val indicatorDao: IndicatorLakeDao,
    private val priceDao: PriceLakeDao,
    private val dashboardDao: DashboardLakeDao,
) {

    @Cacheable("screener", key = "#filter.hashCode()")
    fun list(filter: ScreenerFilter): DashboardPage {
        if (filter.market == null || filter.needsFullQuery()) return dashboardDao.search(filter)
        if (filter.tiers != null && filter.sector != null) return dashboardDao.search(filter)

        val market = Market.valueOf(filter.market)
        return if (filter.tiers != null) listByTier(market, filter.tiers, filter.sector, filter.page, filter.size)
        else listByStock(market, filter.sector, filter.page, filter.size)
    }

    @Cacheable("data-freshness")
    fun dataFreshness(): DataFreshnessResponse = dashboardDao.dataFreshness()

    @Cacheable("sectors", key = "#market?.name ?: 'ALL'")
    fun sectors(market: Market?): List<String> = stockDao.findDistinctSectors(market)

    fun search(q: String, market: Market?, limit: Int): List<StockSearchResult> =
        dashboardDao.searchStocks(q, market, limit)

    private fun listByTier(
        market: Market,
        tiers: List<String>,
        sector: String?,
        page: Int,
        size: Int,
    ): DashboardPage {
        val badgePage = badgeDao.findPageByMarketAndTiers(market, tiers, page, size)
        val stockIds = badgePage.content.map { it.stockId }
        if (stockIds.isEmpty()) return emptyPage(page, size)

        val stockMap = stockDao.findByIds(stockIds).associateBy { it.id }
        val indicatorMap = indicatorDao.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val priceMap = priceDao.findTop2PerStock(stockIds, marketGroupOf(market)).groupBy { it.stockId }

        val items = badgePage.content.mapNotNull { badge ->
            val stock = stockMap[badge.stockId] ?: return@mapNotNull null
            if (sector != null && stock.sector != sector) return@mapNotNull null
            buildItem(stock, badge, indicatorMap[badge.stockId], priceMap[badge.stockId])
        }

        return DashboardPage(
            content = items,
            totalElements = badgePage.totalElements,
            totalPages = totalPages(badgePage.totalElements, size),
            number = page,
            size = size,
            hasNext = badgePage.hasNext,
        )
    }

    private fun listByStock(market: Market, sector: String?, page: Int, size: Int): DashboardPage {
        val stockPage = stockDao.findPageByMarket(market, sector, page, size)
        val stockIds = stockPage.content.map { it.id }
        if (stockIds.isEmpty()) return emptyPage(page, size)

        val badgeMap = badgeDao.findByStockIds(stockIds).associateBy { it.stockId }
        val indicatorMap = indicatorDao.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val priceMap = priceDao.findTop2PerStock(stockIds, marketGroupOf(market)).groupBy { it.stockId }

        val items = stockPage.content.map { stock ->
            buildItem(stock, badgeMap[stock.id], indicatorMap[stock.id], priceMap[stock.id])
        }

        return DashboardPage(
            content = items,
            totalElements = stockPage.totalElements,
            totalPages = totalPages(stockPage.totalElements, size),
            number = page,
            size = size,
            hasNext = stockPage.hasNext,
        )
    }

    private fun buildItem(stock: Stock, badge: RiskBadge?, indicator: StockIndicator?, prices: List<DailyPrice>?): DashboardStockItem {
        val sorted = prices?.sortedByDescending { it.date }
        val latest = sorted?.firstOrNull()?.close
        val prev = sorted?.getOrNull(1)?.close
        val changePct = if (latest != null && prev != null && prev.signum() != 0)
            latest.subtract(prev).multiply(BigDecimal(100)).divide(prev, 2, RoundingMode.HALF_UP).toDouble()
        else null

        return DashboardStockItem(
            stockId = stock.id,
            symbol = stock.symbol,
            name = stock.name,
            market = stock.market.name,
            sector = stock.sector,
            latestClose = latest,
            priceChangePercent = changePct,
            comparedDate = sorted?.getOrNull(1)?.date?.toString(),
            summaryTier = badge?.summaryTier,
            dimensionTiers = badge?.dimensions?.let(::extractDimensionTiers),
            beta = indicator?.beta,
            rsi14 = indicator?.rsi14,
            sharpe = indicator?.sharpe,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractDimensionTiers(dims: Map<String, Any>): Map<String, String>? {
        val list = dims["dims"] as? List<Map<String, Any>> ?: return null
        return list.mapNotNull { d ->
            val name = d["name"]?.toString() ?: return@mapNotNull null
            val tier = d["tier"]?.toString() ?: return@mapNotNull null
            name to tier
        }.toMap().ifEmpty { null }
    }

    private fun totalPages(totalElements: Long, size: Int): Int = ((totalElements + size - 1) / size).toInt()

    private fun emptyPage(page: Int, size: Int) = DashboardPage(
        content = emptyList(),
        totalElements = 0,
        totalPages = 0,
        number = page,
        size = size,
        hasNext = false,
    )
}

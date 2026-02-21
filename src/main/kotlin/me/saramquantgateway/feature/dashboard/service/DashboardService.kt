package me.saramquantgateway.feature.dashboard.service

import me.saramquantgateway.domain.entity.indicator.StockIndicator
import me.saramquantgateway.domain.entity.riskbadge.RiskBadge
import me.saramquantgateway.domain.entity.stock.DailyPrice
import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.repository.indicator.StockIndicatorRepository
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
import me.saramquantgateway.domain.repository.stock.DailyPriceRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.feature.dashboard.dto.DashboardPage
import me.saramquantgateway.feature.dashboard.dto.DashboardStockItem
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode

@Service
class DashboardService(
    private val stockRepo: StockRepository,
    private val badgeRepo: RiskBadgeRepository,
    private val indicatorRepo: StockIndicatorRepository,
    private val priceRepo: DailyPriceRepository,
) {

    fun list(market: Market, tiers: List<String>?, sector: String?, sort: String, page: Int, size: Int): DashboardPage {
        val pageable = PageRequest.of(page, size, Sort.by("name"))
        return if (tiers != null) listByTier(market, tiers, sector, pageable)
        else listByStock(market, sector, pageable)
    }

    fun sectors(market: Market): List<String> = stockRepo.findDistinctSectorsByMarket(market)

    private fun listByTier(market: Market, tiers: List<String>, sector: String?, pageable: PageRequest): DashboardPage {
        val badgePage = badgeRepo.findByMarketAndSummaryTierIn(market, tiers, PageRequest.of(pageable.pageNumber, pageable.pageSize, Sort.by("stockId")))
        val stockIds = badgePage.content.map { it.stockId }
        if (stockIds.isEmpty()) return emptyPage(pageable)

        val stockMap = stockRepo.findByIdIn(stockIds).associateBy { it.id }
        val indicatorMap = indicatorRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val priceMap = priceRepo.findTop2PerStockByStockIdIn(stockIds).groupBy { it.stockId }

        val items = badgePage.content.mapNotNull { badge ->
            val stock = stockMap[badge.stockId] ?: return@mapNotNull null
            if (sector != null && stock.sector != sector) return@mapNotNull null
            buildItem(stock, badge, indicatorMap[badge.stockId], priceMap[badge.stockId])
        }

        return DashboardPage(
            items = items,
            totalCount = badgePage.totalElements,
            totalPages = badgePage.totalPages,
            page = pageable.pageNumber,
            size = pageable.pageSize,
            hasNext = badgePage.hasNext(),
        )
    }

    private fun listByStock(market: Market, sector: String?, pageable: PageRequest): DashboardPage {
        val stockPage = if (sector != null)
            stockRepo.findByMarketAndSectorAndIsActiveTrue(market, sector, pageable)
        else
            stockRepo.findByMarketAndIsActiveTrue(market, pageable)

        val stockIds = stockPage.content.map { it.id }
        if (stockIds.isEmpty()) return emptyPage(pageable)

        val badgeMap = badgeRepo.findByStockIdIn(stockIds).associateBy { it.stockId }
        val indicatorMap = indicatorRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val priceMap = priceRepo.findTop2PerStockByStockIdIn(stockIds).groupBy { it.stockId }

        val items = stockPage.content.map { stock ->
            buildItem(stock, badgeMap[stock.id], indicatorMap[stock.id], priceMap[stock.id])
        }

        return DashboardPage(
            items = items,
            totalCount = stockPage.totalElements,
            totalPages = stockPage.totalPages,
            page = pageable.pageNumber,
            size = pageable.pageSize,
            hasNext = stockPage.hasNext(),
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
            dimensionTiers = badge?.dimensions?.entries?.mapNotNull { (k, v) ->
                val tier = (v as? Map<*, *>)?.get("tier")?.toString()
                if (tier != null) k to tier else null
            }?.toMap(),
            beta = indicator?.beta,
            rsi14 = indicator?.rsi14,
            sharpe = indicator?.sharpe,
        )
    }

    private fun emptyPage(pageable: PageRequest) = DashboardPage(
        items = emptyList(),
        totalCount = 0,
        totalPages = 0,
        page = pageable.pageNumber,
        size = pageable.pageSize,
        hasNext = false,
    )
}

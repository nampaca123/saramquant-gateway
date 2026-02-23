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
import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import me.saramquantgateway.feature.dashboard.dto.StockSearchResult
import me.saramquantgateway.feature.dashboard.repository.DashboardQueryRepository
import jakarta.persistence.EntityManager
import org.springframework.cache.annotation.Cacheable
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
    private val queryRepo: DashboardQueryRepository,
    private val em: EntityManager,
) {

    @Cacheable("screener", key = "#filter.hashCode()")
    fun list(filter: ScreenerFilter): DashboardPage {
        if (filter.market == null || filter.hasAdvancedFilters()) return queryRepo.search(filter)

        val market = Market.valueOf(filter.market)
        val pageable = PageRequest.of(filter.page, filter.size, Sort.by("name"))
        return if (filter.tiers != null) listByTier(market, filter.tiers, filter.sector, pageable)
        else listByStock(market, filter.sector, pageable)
    }

    @Cacheable("sectors", key = "#market?.name ?: 'ALL'")
    fun sectors(market: Market?): List<String> =
        if (market != null) stockRepo.findDistinctSectorsByMarket(market)
        else stockRepo.findDistinctSectors()

    fun search(q: String, market: Market?, limit: Int): List<StockSearchResult> {
        val isSingleChar = q.length == 1
        val whereClause = buildString {
            append("WHERE s.is_active = true")
            if (market != null) append(" AND s.market::text = :market")
            if (isSingleChar) {
                append(" AND LOWER(s.symbol) LIKE :prefix")
            } else {
                append(" AND (s.name ILIKE :query OR s.symbol ILIKE :query)")
            }
        }
        val sql = """
            SELECT s.id, s.symbol, s.name, s.market::text, s.sector
            FROM stocks s $whereClause
            ORDER BY
              CASE WHEN LOWER(s.symbol) LIKE :prefix THEN 0 ELSE 1 END,
              s.name
            LIMIT :lim
        """.trimIndent()

        val query = em.createNativeQuery(sql)
        if (market != null) query.setParameter("market", market.name)
        query.setParameter("prefix", "${q.lowercase()}%")
        if (!isSingleChar) query.setParameter("query", "%$q%")
        query.setParameter("lim", limit)

        @Suppress("UNCHECKED_CAST")
        val rows = query.resultList as List<Array<Any?>>
        return rows.map { row ->
            StockSearchResult(
                stockId = (row[0] as Number).toLong(),
                symbol = row[1] as String,
                name = row[2] as String,
                market = row[3] as String,
                sector = row[4] as? String,
            )
        }
    }

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
            content = items,
            totalElements = badgePage.totalElements,
            totalPages = badgePage.totalPages,
            number = pageable.pageNumber,
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
            content = items,
            totalElements = stockPage.totalElements,
            totalPages = stockPage.totalPages,
            number = pageable.pageNumber,
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

    private fun emptyPage(pageable: PageRequest) = DashboardPage(
        content = emptyList(),
        totalElements = 0,
        totalPages = 0,
        number = pageable.pageNumber,
        size = pageable.pageSize,
        hasNext = false,
    )
}

package me.saramquantgateway.feature.riskbadge.service

import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.feature.riskbadge.dto.RiskBadgeItem
import me.saramquantgateway.feature.riskbadge.dto.RiskBadgePage
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service

@Service
class RiskBadgeService(
    private val badgeRepo: RiskBadgeRepository,
    private val stockRepo: StockRepository,
) {

    fun list(market: Market, tier: String?, page: Int, size: Int): RiskBadgePage {
        val pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "date"))

        val badges = if (tier != null)
            badgeRepo.findByMarketAndSummaryTier(market, tier, pageable)
        else
            badgeRepo.findByMarket(market, pageable)

        val stockMap = stockRepo.findByIdIn(badges.content.map { it.stockId })
            .associateBy { it.id }

        val items = badges.content.mapNotNull { badge ->
            val stock = stockMap[badge.stockId] ?: return@mapNotNull null
            RiskBadgeItem(
                stockId = stock.id,
                symbol = stock.symbol,
                name = stock.name,
                market = stock.market.name,
                sector = stock.sector,
                summaryTier = badge.summaryTier,
                date = badge.date.toString(),
                dimensions = badge.dimensions,
            )
        }

        return RiskBadgePage(
            items = items,
            totalCount = badges.totalElements,
            totalPages = badges.totalPages,
            page = page,
            size = size,
            hasNext = badges.hasNext(),
        )
    }
}

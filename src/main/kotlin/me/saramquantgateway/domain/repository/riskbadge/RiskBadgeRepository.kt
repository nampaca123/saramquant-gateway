package me.saramquantgateway.domain.repository.riskbadge

import me.saramquantgateway.domain.entity.riskbadge.RiskBadge
import me.saramquantgateway.domain.enum.stock.Market
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface RiskBadgeRepository : JpaRepository<RiskBadge, Long> {

    fun findByStockId(stockId: Long): RiskBadge?

    fun findByStockIdIn(stockIds: List<Long>): List<RiskBadge>

    fun findByMarket(market: Market, pageable: Pageable): Page<RiskBadge>

    fun findByMarketAndSummaryTier(market: Market, summaryTier: String, pageable: Pageable): Page<RiskBadge>

    fun findByMarketAndSummaryTierIn(market: Market, summaryTiers: List<String>, pageable: Pageable): Page<RiskBadge>

    @Query("SELECT rb.market, rb.summaryTier, COUNT(rb) FROM RiskBadge rb GROUP BY rb.market, rb.summaryTier")
    fun countByMarketAndTier(): List<Array<Any>>
}

package me.saramquantgateway.feature.portfolio.dto

import java.math.BigDecimal
import java.time.Instant

data class PortfolioSummary(
    val id: Long,
    val marketGroup: String,
    val holdingsCount: Int,
    val createdAt: Instant,
)

data class HoldingDetail(
    val id: Long,
    val stockId: Long,
    val symbol: String,
    val name: String,
    val shares: BigDecimal,
    val avgPrice: BigDecimal,
    val currency: String,
    val purchasedAt: String,
    val purchaseFxRate: BigDecimal? = null,
    val priceSource: String,
)

data class PortfolioDetail(
    val id: Long,
    val marketGroup: String,
    val holdings: List<HoldingDetail>,
    val createdAt: Instant,
)

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
    val market: String? = null,
    val sector: String? = null,
    val shares: BigDecimal,
    val avgPrice: BigDecimal,
    val currency: String,
    val purchasedAt: String,
    val purchaseFxRate: BigDecimal? = null,
    val priceSource: String,
    val latestClose: BigDecimal? = null,
    val priceChangePercent: Double? = null,
    val summaryTier: String? = null,
    val dimensionTiers: Map<String, String>? = null,
    val unrealizedPnl: BigDecimal? = null,
    val unrealizedPnlPercent: Double? = null,
    val currentValue: BigDecimal? = null,
    val costBasis: BigDecimal? = null,
)

data class PortfolioDetail(
    val id: Long,
    val marketGroup: String,
    val holdings: List<HoldingDetail>,
    val createdAt: Instant,
    val totalCost: BigDecimal? = null,
    val totalValue: BigDecimal? = null,
    val totalPnl: BigDecimal? = null,
    val totalPnlPercent: Double? = null,
)

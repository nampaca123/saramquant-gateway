package me.saramquantgateway.feature.home.dto

import me.saramquantgateway.feature.portfolio.dto.PortfolioSummary
import java.math.BigDecimal

data class HomeSummary(
    val benchmarks: List<BenchmarkSummary>,
    val marketOverview: MarketOverview,
    val portfolios: List<PortfolioSummary>?,
)

data class BenchmarkSummary(
    val benchmark: String,
    val latestClose: BigDecimal,
    val previousClose: BigDecimal,
    val changePercent: Double,
    val date: String,
)

data class MarketOverview(
    val tierDistribution: List<MarketTierCount>,
    val totalStocks: Int,
)

data class MarketTierCount(
    val market: String,
    val tier: String,
    val count: Int,
)

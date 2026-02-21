package me.saramquantgateway.feature.dashboard.dto

import java.math.BigDecimal

data class DashboardStockItem(
    val stockId: Long,
    val symbol: String,
    val name: String,
    val market: String,
    val sector: String?,
    val latestClose: BigDecimal?,
    val priceChangePercent: Double?,
    val comparedDate: String?,
    val summaryTier: String?,
    val dimensionTiers: Map<String, String>?,
    val beta: BigDecimal?,
    val rsi14: BigDecimal?,
    val sharpe: BigDecimal?,
)

data class DashboardPage(
    val items: List<DashboardStockItem>,
    val totalCount: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
)

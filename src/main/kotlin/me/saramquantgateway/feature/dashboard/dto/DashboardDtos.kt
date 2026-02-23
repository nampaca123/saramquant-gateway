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
    val per: BigDecimal? = null,
    val pbr: BigDecimal? = null,
    val roe: BigDecimal? = null,
    val debtRatio: BigDecimal? = null,
    val adx14: BigDecimal? = null,
    val atr14: BigDecimal? = null,
)

data class DashboardPage(
    val content: List<DashboardStockItem>,
    val totalElements: Long,
    val totalPages: Int,
    val number: Int,
    val size: Int,
    val hasNext: Boolean,
)

data class ScreenerFilter(
    val market: String? = null,
    val tiers: List<String>? = null,
    val sector: String? = null,
    val sort: String = "name_asc",
    val page: Int = 0,
    val size: Int = 20,
    val betaMin: BigDecimal? = null,
    val betaMax: BigDecimal? = null,
    val rsiMin: BigDecimal? = null,
    val rsiMax: BigDecimal? = null,
    val sharpeMin: BigDecimal? = null,
    val sharpeMax: BigDecimal? = null,
    val atrMin: BigDecimal? = null,
    val atrMax: BigDecimal? = null,
    val adxMin: BigDecimal? = null,
    val adxMax: BigDecimal? = null,
    val perMin: BigDecimal? = null,
    val perMax: BigDecimal? = null,
    val pbrMin: BigDecimal? = null,
    val pbrMax: BigDecimal? = null,
    val roeMin: BigDecimal? = null,
    val roeMax: BigDecimal? = null,
    val debtRatioMin: BigDecimal? = null,
    val debtRatioMax: BigDecimal? = null,
    val query: String? = null,
) {
    fun hasAdvancedFilters(): Boolean = query != null || listOfNotNull(
        betaMin, betaMax, rsiMin, rsiMax, sharpeMin, sharpeMax,
        atrMin, atrMax, adxMin, adxMax,
        perMin, perMax, pbrMin, pbrMax, roeMin, roeMax,
        debtRatioMin, debtRatioMax,
    ).isNotEmpty()
}

data class StockSearchResult(
    val stockId: Long,
    val symbol: String,
    val name: String,
    val market: String,
    val sector: String?,
)

data class DataFreshnessResponse(
    val krPriceDate: String?,
    val krPriceCollectedAt: String?,
    val usPriceDate: String?,
    val usPriceCollectedAt: String?,
    val krFinancialCollectedAt: String?,
    val usFinancialCollectedAt: String?,
)

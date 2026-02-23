package me.saramquantgateway.feature.stock.dto

import java.math.BigDecimal

data class StockDetailResponse(
    val header: StockHeader,
    val riskBadge: RiskBadgeDetail?,
    val indicators: IndicatorSnapshot?,
    val fundamentals: FundamentalSnapshot?,
    val sectorComparison: SectorComparisonSnapshot?,
    val factorExposures: FactorExposureSnapshot?,
    val llmAnalysis: CachedLlmAnalysis?,
)

data class StockHeader(
    val stockId: Long,
    val symbol: String,
    val name: String,
    val market: String,
    val sector: String?,
    val latestClose: BigDecimal?,
    val priceChangePercent: Double?,
    val latestDate: String?,
)

data class RiskBadgeDetail(
    val summaryTier: String,
    val date: String,
    val dimensions: Map<String, Any>,
)

data class IndicatorSnapshot(
    val date: String,
    val rsi14: BigDecimal?, val macd: BigDecimal?, val macdSignal: BigDecimal?, val macdHist: BigDecimal?,
    val stochK: BigDecimal?, val stochD: BigDecimal?,
    val bbUpper: BigDecimal?, val bbMiddle: BigDecimal?, val bbLower: BigDecimal?,
    val adx14: BigDecimal?, val plusDi: BigDecimal?, val minusDi: BigDecimal?,
    val atr14: BigDecimal?,
    val sma20: BigDecimal?, val ema20: BigDecimal?,
    val sar: BigDecimal?,
    val obv: Long?, val vma20: Long?,
    val beta: BigDecimal?, val alpha: BigDecimal?, val sharpe: BigDecimal?,
)

data class FundamentalSnapshot(
    val date: String,
    val per: BigDecimal?, val pbr: BigDecimal?, val eps: BigDecimal?, val bps: BigDecimal?,
    val roe: BigDecimal?, val debtRatio: BigDecimal?, val operatingMargin: BigDecimal?,
)

data class SectorComparisonSnapshot(
    val sector: String,
    val stockCount: Int,
    val medianPer: BigDecimal?, val medianPbr: BigDecimal?, val medianRoe: BigDecimal?,
    val medianOperatingMargin: BigDecimal?, val medianDebtRatio: BigDecimal?,
)

data class FactorExposureSnapshot(
    val date: String,
    val sizeZ: BigDecimal?, val valueZ: BigDecimal?, val momentumZ: BigDecimal?,
    val volatilityZ: BigDecimal?, val qualityZ: BigDecimal?, val leverageZ: BigDecimal?,
)

data class FinancialStatementSnapshot(
    val fiscalYear: Int,
    val revenue: BigDecimal?, val operatingIncome: BigDecimal?, val netIncome: BigDecimal?,
    val totalAssets: BigDecimal?, val totalEquity: BigDecimal?,
    val revenueGrowthPct: Double?, val netIncomeGrowthPct: Double?,
)

data class CachedLlmAnalysis(
    val preset: String,
    val lang: String,
    val analysis: String,
    val model: String,
    val createdAt: String,
)

data class PriceSeriesResponse(
    val symbol: String,
    val market: String,
    val period: String,
    val prices: List<PricePoint>,
)

data class PricePoint(
    val date: String,
    val open: BigDecimal, val high: BigDecimal, val low: BigDecimal, val close: BigDecimal,
    val volume: Long,
)

data class BenchmarkComparisonResponse(
    val symbol: String,
    val benchmark: String,
    val period: String,
    val stockSeries: List<NormalizedPoint>,
    val benchmarkSeries: List<NormalizedPoint>,
)

data class NormalizedPoint(
    val date: String,
    val value: Double,
)

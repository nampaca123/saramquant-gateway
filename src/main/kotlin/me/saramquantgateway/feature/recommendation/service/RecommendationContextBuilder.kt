package me.saramquantgateway.feature.recommendation.service

import me.saramquantgateway.domain.entity.factor.FactorExposure
import me.saramquantgateway.domain.entity.fundamental.StockFundamental
import me.saramquantgateway.domain.entity.indicator.StockIndicator
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.repository.factor.FactorCovarianceRepository
import me.saramquantgateway.domain.repository.factor.FactorExposureRepository
import me.saramquantgateway.domain.repository.fundamental.StockFundamentalRepository
import me.saramquantgateway.domain.repository.indicator.StockIndicatorRepository
import me.saramquantgateway.domain.repository.market.SectorAggregateRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.feature.portfolio.dto.PortfolioDetail
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class RecommendationContextBuilder(
    private val stockRepo: StockRepository,
    private val indicatorRepo: StockIndicatorRepository,
    private val fundamentalRepo: StockFundamentalRepository,
    private val factorExposureRepo: FactorExposureRepository,
    private val factorCovRepo: FactorCovarianceRepository,
    private val sectorAggRepo: SectorAggregateRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val FACTOR_NAMES = listOf("size", "value", "momentum", "volatility", "quality", "leverage")
        private val FACTOR_SHORT = listOf("sz", "va", "mo", "vo", "qu", "le")

        fun expandMarketGroup(group: String): List<Market> = when (group.uppercase()) {
            "KR" -> listOf(Market.KR_KOSPI, Market.KR_KOSDAQ)
            "US" -> listOf(Market.US_NYSE, Market.US_NASDAQ)
            else -> try { listOf(Market.valueOf(group)) } catch (_: Exception) { emptyList() }
        }

        fun expandMarketGroupStrings(group: String): List<String> = when (group.uppercase()) {
            "KR" -> listOf("KR_KOSPI", "KR_KOSDAQ")
            "US" -> listOf("US_NYSE", "US_NASDAQ")
            else -> listOf(group)
        }
    }

    data class PrecomputedContext(
        val holdingsTable: String,
        val riskEvaluation: String,
        val sectorOverview: String,
        val holdingStockIds: List<Long>,
    )

    fun build(portfolio: PortfolioDetail, lang: String): PrecomputedContext? {
        if (portfolio.holdings.isEmpty()) return null

        val stockIds = portfolio.holdings.map { it.stockId }
        val indicators = indicatorRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val fundamentals = fundamentalRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val factors = factorExposureRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }

        val holdingsTable = buildHoldingsTable(portfolio, indicators, fundamentals, factors)
        val riskEvaluation = buildRiskEvaluation(portfolio, stockIds, factors, lang)
        val sectorOverview = buildSectorOverview(portfolio.marketGroup, lang)

        return PrecomputedContext(holdingsTable, riskEvaluation, sectorOverview, stockIds)
    }

    private fun buildHoldingsTable(
        portfolio: PortfolioDetail,
        indicators: Map<Long, StockIndicator>,
        fundamentals: Map<Long, StockFundamental>,
        factors: Map<Long, FactorExposure>,
    ): String {
        val totalValue = portfolio.totalValue ?: return ""
        val currency = portfolio.holdings.first().currency
        val sb = StringBuilder()

        sb.appendLine("${totalValue} ${currency} | PnL: ${portfolio.totalPnl} (${portfolio.totalPnlPercent}%)")
        sb.appendLine()
        sb.appendLine("| id | Stock | Wt% | PnL% | Tier | β | Sh | PE | ROE | DR | f.sz | f.va | f.mo | f.vo | f.qu | f.le |")
        sb.appendLine("|---|-------|-----|------|------|---|----|----|-----|----|------|------|------|------|------|------|")

        for (h in portfolio.holdings) {
            val weight = h.currentValue?.let { v ->
                v.multiply(BigDecimal(100)).divide(totalValue, 1, RoundingMode.HALF_UP)
            } ?: "-"
            val pnl = h.unrealizedPnlPercent?.let { String.format("%+.1f", it) } ?: "-"
            val ind = indicators[h.stockId]
            val fund = fundamentals[h.stockId]
            val fac = factors[h.stockId]

            sb.appendLine("| ${h.stockId} | ${h.name}(${h.symbol}) | $weight | $pnl | ${h.summaryTier ?: "-"} | ${fmt(ind?.beta)} | ${fmt(ind?.sharpe)} | ${fmt(fund?.per)} | ${fmt(fund?.roe)} | ${fmt(fund?.debtRatio, 0)} | ${fmt(fac?.sizeZ)} | ${fmt(fac?.valueZ)} | ${fmt(fac?.momentumZ)} | ${fmt(fac?.volatilityZ)} | ${fmt(fac?.qualityZ)} | ${fmt(fac?.leverageZ)} |")
        }

        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildRiskEvaluation(
        portfolio: PortfolioDetail,
        stockIds: List<Long>,
        factors: Map<Long, FactorExposure>,
        lang: String,
    ): String {
        val totalValue = portfolio.totalValue ?: return ""
        val weights = portfolio.holdings.map { h ->
            h.currentValue?.divide(totalValue, 4, RoundingMode.HALF_UP)?.toDouble() ?: 0.0
        }

        val portFactors = DoubleArray(6)
        stockIds.forEachIndexed { i, id ->
            val f = factors[id] ?: return@forEachIndexed
            val w = weights[i]
            portFactors[0] += (f.sizeZ?.toDouble() ?: 0.0) * w
            portFactors[1] += (f.valueZ?.toDouble() ?: 0.0) * w
            portFactors[2] += (f.momentumZ?.toDouble() ?: 0.0) * w
            portFactors[3] += (f.volatilityZ?.toDouble() ?: 0.0) * w
            portFactors[4] += (f.qualityZ?.toDouble() ?: 0.0) * w
            portFactors[5] += (f.leverageZ?.toDouble() ?: 0.0) * w
        }

        val entities = stockRepo.findAllById(stockIds).associateBy { it.id }
        val firstMarket = entities.values.firstOrNull()?.market
        var estimatedVol: Double? = null
        if (firstMarket != null) {
            val cov = factorCovRepo.findTop1ByMarketOrderByDateDesc(firstMarket)
            if (cov != null) {
                val matrix = objectMapper.readValue(cov.matrix, List::class.java) as List<List<Number>>
                if (matrix.size == 6) {
                    var variance = 0.0
                    for (r in 0..5) for (c in 0..5) {
                        variance += portFactors[r] * portFactors[c] * matrix[r][c].toDouble()
                    }
                    estimatedVol = Math.sqrt(Math.max(variance, 0.0) * 252)
                }
            }
        }

        val sectorDist = mutableMapOf<String, Double>()
        stockIds.forEachIndexed { i, id ->
            val sector = entities[id]?.sector ?: "Unknown"
            sectorDist[sector] = (sectorDist[sector] ?: 0.0) + weights[i]
        }
        val hhi = weights.sumOf { it * it }

        val warnings = mutableListOf<String>()
        FACTOR_NAMES.forEachIndexed { i, name ->
            val v = portFactors[i]
            if (v > 1.0) warnings.add("High $name (+${String.format("%.1f", v)}σ)")
            if (v < -1.0) warnings.add("Low $name (${String.format("%.1f", v)}σ)")
        }
        if (hhi > 0.25) warnings.add("HHI=${String.format("%.2f", hhi)}")
        val maxSec = sectorDist.maxByOrNull { it.value }
        if (maxSec != null && maxSec.value > 0.4)
            warnings.add("${maxSec.key} ${String.format("%.0f", maxSec.value * 100)}%")

        val factorStr = FACTOR_SHORT.zip(portFactors.toList()).joinToString(" ") { (n, v) -> "$n=${String.format("%.1f", v)}" }
        val volStr = estimatedVol?.let { String.format("%.1f%%", it * 100) } ?: "N/A"
        val warnStr = if (warnings.isEmpty()) (if (lang == "en") "None" else "없음") else warnings.joinToString(", ")

        return "Factors: $factorStr\nVol: $volStr | HHI: ${String.format("%.2f", hhi)} | EffN: ${if (hhi > 0) String.format("%.1f", 1.0 / hhi) else "N/A"}\nWarnings: $warnStr"
    }

    private fun buildSectorOverview(marketGroup: String, lang: String): String {
        val markets = expandMarketGroup(marketGroup)
        val sb = StringBuilder()

        sb.appendLine(if (lang == "en") "Sector Overview ($marketGroup)" else "섹터 개요 ($marketGroup)")
        sb.appendLine("| Sector | N | mPE | mROE | mDR |")
        sb.appendLine("|--------|---|-----|------|-----|")

        for (mkt in markets) {
            val latest = sectorAggRepo.findTop1ByMarketOrderByDateDesc(mkt) ?: continue
            for (s in sectorAggRepo.findByMarketAndDate(mkt, latest.date)) {
                sb.appendLine("| ${s.sector} | ${s.stockCount} | ${fmt(s.medianPer)} | ${fmt(s.medianRoe)} | ${fmt(s.medianDebtRatio, 0)} |")
            }
        }

        return sb.toString()
    }

    private fun fmt(v: BigDecimal?, decimals: Int = 1): String =
        v?.setScale(decimals, RoundingMode.HALF_UP)?.toPlainString() ?: "-"
}

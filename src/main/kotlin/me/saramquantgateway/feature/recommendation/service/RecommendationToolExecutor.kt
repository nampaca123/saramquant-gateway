package me.saramquantgateway.feature.recommendation.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.domain.enum.recommendation.RecommendationDirection
import me.saramquantgateway.domain.enum.recommendation.RecommendationDirection.*
import me.saramquantgateway.domain.repository.factor.FactorCovarianceRepository
import me.saramquantgateway.domain.repository.factor.FactorExposureRepository
import me.saramquantgateway.domain.repository.fundamental.StockFundamentalRepository
import me.saramquantgateway.domain.repository.indicator.StockIndicatorRepository
import me.saramquantgateway.domain.repository.market.SectorAggregateRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import me.saramquantgateway.feature.dashboard.service.DashboardService
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

@Component
class RecommendationToolExecutor(
    private val dashboardService: DashboardService,
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
    }

    var direction: RecommendationDirection = IMPROVE

    fun execute(toolName: String, input: Map<String, Any?>): String = try {
        when (toolName) {
            "find_candidates" -> findCandidates(input)
            "validate_portfolio" -> validatePortfolio(input)
            else -> objectMapper.writeValueAsString(mapOf("error" to "Unknown tool: $toolName"))
        }
    } catch (e: Exception) {
        objectMapper.writeValueAsString(mapOf("error" to (e.message ?: "Unknown error")))
    }

    fun resolveStockName(toolName: String, input: Map<String, Any?>): String? = null

    // ── find_candidates: direction-preset filters + AI sectors/sort + auto enrichment ──

    @Suppress("UNCHECKED_CAST")
    private fun findCandidates(input: Map<String, Any?>): String {
        val marketGroup = input["market"] as? String ?: "KR"
        val markets = RecommendationContextBuilder.expandMarketGroupStrings(marketGroup)
        val sectors = (input["sectors"] as? List<*>)?.filterIsInstance<String>()
        val excludeSectors = (input["exclude_sectors"] as? List<*>)?.filterIsInstance<String>()
        val excludeIds = (input["exclude_stock_ids"] as? List<*>)?.mapNotNull { (it as? Number)?.toLong() }
        val sort = input["sort"] as? String ?: "sharpe_desc"
        val limit = ((input["limit"] as? Number)?.toInt() ?: 8).coerceIn(1, 15)

        val preset = directionPreset(direction)

        val effectiveSectors = when {
            sectors != null -> sectors
            excludeSectors != null -> null
            else -> null
        }

        val filter = ScreenerFilter(
            markets = markets,
            sectors = effectiveSectors,
            excludeStockIds = excludeIds,
            tiers = preset.tiers,
            sort = sort,
            size = limit,
            betaMax = preset.betaMax,
            sharpeMin = preset.sharpeMin,
            roeMin = preset.roeMin,
            debtRatioMax = preset.debtRatioMax,
        )

        var page = dashboardService.list(filter)

        // If sectors filter returned 0, try without sector constraint
        if (page.content.isEmpty() && effectiveSectors != null) {
            page = dashboardService.list(filter.copy(sectors = null))
        }

        // Filter out excluded sectors client-side (SQL handles sectors IN but not NOT IN for the sectors field)
        val items = if (excludeSectors != null) {
            page.content.filter { it.sector == null || it.sector !in excludeSectors }
        } else {
            page.content
        }

        val stockIds = items.map { it.stockId }
        val factors = if (stockIds.isNotEmpty()) factorExposureRepo.findLatestByStockIds(stockIds).associateBy { it.stockId } else emptyMap()
        val sectorAggs = buildSectorMedians(markets)

        val candidates = items.map { s ->
            val fac = factors[s.stockId]
            val sectorMed = s.sector?.let { sectorAggs[it] }

            val base = mutableMapOf<String, Any?>(
                "stockId" to s.stockId, "symbol" to s.symbol, "name" to s.name,
                "sector" to s.sector, "riskTier" to s.summaryTier,
                "beta" to s.beta, "sharpe" to s.sharpe, "per" to s.per,
                "roePercent" to s.roe?.multiply(BigDecimal(100))?.setScale(1, RoundingMode.HALF_UP),
                "debtRatioPercent" to s.debtRatio?.multiply(BigDecimal(100))?.setScale(1, RoundingMode.HALF_UP),
            )

            if (fac != null) {
                base["factors"] = mapOf(
                    "size" to fac.sizeZ, "value" to fac.valueZ, "momentum" to fac.momentumZ,
                    "volatility" to fac.volatilityZ, "quality" to fac.qualityZ, "leverage" to fac.leverageZ,
                )
            }

            if (sectorMed != null) {
                base["vsSector"] = mapOf(
                    "per" to rankVsMedian(s.per, sectorMed.medianPer),
                    "roe" to rankVsMedian(s.roe, sectorMed.medianRoe, higherIsBetter = true),
                )
            }

            base
        }

        return objectMapper.writeValueAsString(mapOf("candidates" to candidates, "totalMatched" to page.totalElements))
    }

    // ── validate_portfolio: same as old evaluate_portfolio ──

    @Suppress("UNCHECKED_CAST")
    private fun validatePortfolio(input: Map<String, Any?>): String {
        val stocks = input["stocks"] as List<Map<String, Any>>
        val stockIds = stocks.map { (it["stock_id"] as Number).toLong() }
        val weights = stocks.map { (it["weight"] as Number).toDouble() }

        val stockEntities = stockRepo.findAllById(stockIds).associateBy { it.id }
        val factors = factorExposureRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val indicators = indicatorRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val fundamentals = fundamentalRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }

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

        val firstMarket = stockEntities.values.firstOrNull()?.market
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
            val sector = stockEntities[id]?.sector ?: "Unknown"
            sectorDist[sector] = (sectorDist[sector] ?: 0.0) + weights[i]
        }
        val hhi = weights.sumOf { it * it }

        fun weightedAvg(extract: (Long) -> BigDecimal?): Double? {
            var sum = 0.0; var wSum = 0.0
            stockIds.forEachIndexed { i, id ->
                val v = extract(id)?.toDouble() ?: return@forEachIndexed
                sum += v * weights[i]; wSum += weights[i]
            }
            return if (wSum > 0) sum / wSum else null
        }

        val warnings = mutableListOf<String>()
        FACTOR_NAMES.forEachIndexed { i, name ->
            val v = portFactors[i]
            if (v > 1.0) warnings.add("High $name factor tilt (+${String.format("%.1f", v)}σ)")
            if (v < -1.0) warnings.add("Low $name factor tilt (${String.format("%.1f", v)}σ)")
        }
        if (hhi > 0.25) warnings.add("High concentration (HHI=${String.format("%.2f", hhi)})")
        val maxSector = sectorDist.maxByOrNull { it.value }
        if (maxSector != null && maxSector.value > 0.4)
            warnings.add("Sector concentration: ${maxSector.key} at ${String.format("%.0f", maxSector.value * 100)}%")

        val result = mapOf(
            "factorExposure" to FACTOR_NAMES.zip(portFactors.toList()).associate { (n, v) -> n to BigDecimal(v).setScale(2, RoundingMode.HALF_UP) },
            "estimatedAnnualVolatility" to estimatedVol?.let { BigDecimal(it).setScale(4, RoundingMode.HALF_UP) },
            "concentration" to mapOf(
                "hhi" to BigDecimal(hhi).setScale(3, RoundingMode.HALF_UP),
                "effectiveN" to if (hhi > 0) BigDecimal(1.0 / hhi).setScale(1, RoundingMode.HALF_UP) else null,
                "maxWeight" to weights.maxOrNull(),
                "sectorDistribution" to sectorDist.mapValues { BigDecimal(it.value).setScale(2, RoundingMode.HALF_UP) },
            ),
            "weightedMetrics" to mapOf(
                "beta" to weightedAvg { indicators[it]?.beta },
                "sharpe" to weightedAvg { indicators[it]?.sharpe },
                "per" to weightedAvg { fundamentals[it]?.per },
                "roePercent" to weightedAvg { fundamentals[it]?.roe }?.let { it * 100 },
                "debtRatioPercent" to weightedAvg { fundamentals[it]?.debtRatio }?.let { it * 100 },
            ),
            "warnings" to warnings,
        )
        return objectMapper.writeValueAsString(result)
    }

    // ── Direction presets ──

    private data class DirectionPreset(
        val tiers: List<String>?,
        val betaMax: BigDecimal?,
        val sharpeMin: BigDecimal?,
        val roeMin: BigDecimal?,
        val debtRatioMax: BigDecimal?,
    )

    private fun directionPreset(dir: RecommendationDirection) = when (dir) {
        CONSERVATIVE -> DirectionPreset(
            tiers = listOf("STABLE"),
            betaMax = BigDecimal("0.8"),
            sharpeMin = BigDecimal("0.3"),
            roeMin = BigDecimal("0.05"),
            debtRatioMax = BigDecimal("1.5"),
        )
        GROWTH -> DirectionPreset(
            tiers = null,
            betaMax = null,
            sharpeMin = BigDecimal("0.2"),
            roeMin = BigDecimal("0.08"),
            debtRatioMax = BigDecimal("2.5"),
        )
        IMPROVE -> DirectionPreset(
            tiers = listOf("STABLE", "CAUTION"),
            betaMax = null,
            sharpeMin = BigDecimal("0.2"),
            roeMin = BigDecimal("0.05"),
            debtRatioMax = BigDecimal("2.0"),
        )
    }

    // ── Helpers ──

    private data class SectorMedian(val medianPer: BigDecimal?, val medianRoe: BigDecimal?)

    private fun buildSectorMedians(markets: List<String>): Map<String, SectorMedian> {
        val result = mutableMapOf<String, SectorMedian>()
        for (mktStr in markets) {
            val mkt = try { me.saramquantgateway.domain.enum.stock.Market.valueOf(mktStr) } catch (_: Exception) { continue }
            val latest = sectorAggRepo.findTop1ByMarketOrderByDateDesc(mkt) ?: continue
            for (s in sectorAggRepo.findByMarketAndDate(mkt, latest.date)) {
                result[s.sector] = SectorMedian(s.medianPer, s.medianRoe)
            }
        }
        return result
    }

    private fun rankVsMedian(value: BigDecimal?, median: BigDecimal?, higherIsBetter: Boolean = false): String {
        if (value == null || median == null || median.signum() == 0) return "N/A"
        val ratio = value.toDouble() / median.toDouble()
        return if (higherIsBetter) {
            when { ratio >= 1.2 -> "well_above"; ratio >= 1.0 -> "above_median"; ratio >= 0.8 -> "below_median"; else -> "well_below" }
        } else {
            when { ratio <= 0.8 -> "cheap"; ratio <= 1.0 -> "below_median"; ratio <= 1.2 -> "above_median"; else -> "expensive" }
        }
    }
}

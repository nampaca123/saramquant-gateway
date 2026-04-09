package me.saramquantgateway.feature.recommendation.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.repository.factor.FactorCovarianceRepository
import me.saramquantgateway.domain.repository.factor.FactorExposureRepository
import me.saramquantgateway.domain.repository.fundamental.StockFundamentalRepository
import me.saramquantgateway.domain.repository.indicator.StockIndicatorRepository
import me.saramquantgateway.domain.repository.market.RiskFreeRateRepository
import me.saramquantgateway.domain.repository.market.SectorAggregateRepository
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
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
    private val badgeRepo: RiskBadgeRepository,
    private val factorExposureRepo: FactorExposureRepository,
    private val factorCovRepo: FactorCovarianceRepository,
    private val sectorAggRepo: SectorAggregateRepository,
    private val riskFreeRateRepo: RiskFreeRateRepository,
    private val objectMapper: ObjectMapper,
) {
    companion object {
        private val FACTOR_NAMES = listOf("size", "value", "momentum", "volatility", "quality", "leverage")
    }

    fun execute(toolName: String, input: Map<String, Any?>): String = try {
        when (toolName) {
            "screen_stocks" -> screenStocks(input)
            "get_stock_detail" -> getStockDetail(input)
            "get_sector_overview" -> getSectorOverview(input)
            "evaluate_portfolio" -> evaluatePortfolio(input)
            else -> objectMapper.writeValueAsString(mapOf("error" to "Unknown tool: $toolName"))
        }
    } catch (e: Exception) {
        objectMapper.writeValueAsString(mapOf("error" to (e.message ?: "Unknown error")))
    }

    fun resolveStockName(toolName: String, input: Map<String, Any?>): String? {
        if (toolName != "get_stock_detail") return null
        val stockId = (input["stock_id"] as Number).toLong()
        return stockRepo.findById(stockId).orElse(null)?.name
    }

    private fun screenStocks(input: Map<String, Any?>): String {
        val filter = ScreenerFilter(
            market = input["market"] as? String,
            tiers = asList(input["tiers"]),
            sector = input["sector"] as? String,
            sort = input["sort"] as? String ?: "sharpe_desc",
            size = ((input["size"] as? Number)?.toInt() ?: 20).coerceAtMost(40),
            betaMin = toBd(input["beta_min"]), betaMax = toBd(input["beta_max"]),
            sharpeMin = toBd(input["sharpe_min"]),
            roeMin = toBd(input["roe_min"]),
            debtRatioMax = toBd(input["debt_ratio_max"]),
            perMax = toBd(input["per_max"]),
        )
        val page = dashboardService.list(filter)
        val items = page.content.map { s ->
            mapOf(
                "stockId" to s.stockId, "symbol" to s.symbol, "name" to s.name,
                "market" to s.market, "sector" to s.sector, "summaryTier" to s.summaryTier,
                "beta" to s.beta, "sharpe" to s.sharpe, "per" to s.per, "pbr" to s.pbr,
                "roe" to s.roe, "debtRatio" to s.debtRatio,
            )
        }
        return objectMapper.writeValueAsString(mapOf("stocks" to items, "total" to page.totalElements))
    }

    private fun getStockDetail(input: Map<String, Any?>): String {
        val stockId = (input["stock_id"] as Number).toLong()
        val stock = stockRepo.findById(stockId).orElseThrow { IllegalArgumentException("Stock not found: $stockId") }

        val indicator = indicatorRepo.findTop1ByStockIdOrderByDateDesc(stockId)
        val fundamental = fundamentalRepo.findTop1ByStockIdOrderByDateDesc(stockId)
        val badge = badgeRepo.findByStockId(stockId)
        val factor = factorExposureRepo.findTop1ByStockIdOrderByDateDesc(stockId)
        val sectorAgg = stock.sector?.let { sectorAggRepo.findTop1ByMarketAndSectorOrderByDateDesc(stock.market, it) }
        val country = Country.forMarket(stock.market)
        val riskFreeRate = riskFreeRateRepo.findTop1ByCountryAndMaturityOrderByDateDesc(country, Maturity.Y1)?.rate

        val result = mutableMapOf<String, Any?>(
            "stockId" to stock.id, "symbol" to stock.symbol, "name" to stock.name,
            "market" to stock.market.name, "sector" to stock.sector,
        )

        indicator?.let {
            result["indicators"] = mapOf(
                "date" to it.date, "rsi14" to it.rsi14, "macd" to it.macd,
                "macdSignal" to it.macdSignal, "macdHist" to it.macdHist,
                "stochK" to it.stochK, "stochD" to it.stochD,
                "bbUpper" to it.bbUpper, "bbMiddle" to it.bbMiddle, "bbLower" to it.bbLower,
                "adx14" to it.adx14, "atr14" to it.atr14,
                "sma20" to it.sma20, "ema20" to it.ema20,
                "beta" to it.beta, "alpha" to it.alpha, "sharpe" to it.sharpe,
            )
        }

        fundamental?.let {
            result["fundamentals"] = mapOf(
                "date" to it.date, "per" to it.per, "pbr" to it.pbr,
                "eps" to it.eps, "bps" to it.bps,
                "roe" to it.roe, "debtRatio" to it.debtRatio, "operatingMargin" to it.operatingMargin,
            )
        }

        factor?.let {
            result["factorExposure"] = mapOf(
                "date" to it.date,
                "size" to it.sizeZ, "value" to it.valueZ, "momentum" to it.momentumZ,
                "volatility" to it.volatilityZ, "quality" to it.qualityZ, "leverage" to it.leverageZ,
            )
        }

        badge?.let {
            result["riskBadge"] = mapOf("summaryTier" to it.summaryTier, "dimensions" to it.dimensions)
        }

        sectorAgg?.let {
            result["sectorComparison"] = mapOf(
                "sector" to it.sector, "stockCount" to it.stockCount,
                "medianPer" to it.medianPer, "medianPbr" to it.medianPbr, "medianRoe" to it.medianRoe,
                "medianOpMargin" to it.medianOperatingMargin, "medianDebtRatio" to it.medianDebtRatio,
            )
        }

        riskFreeRate?.let { result["riskFreeRate"] = it }

        return objectMapper.writeValueAsString(result)
    }

    private fun getSectorOverview(input: Map<String, Any?>): String {
        val market = Market.valueOf(input["market"] as String)
        val latest = sectorAggRepo.findTop1ByMarketOrderByDateDesc(market)
        val date = latest?.date ?: return objectMapper.writeValueAsString(mapOf("sectors" to emptyList<Any>()))

        val sectors = sectorAggRepo.findByMarketAndDate(market, date).map { s ->
            mapOf(
                "sector" to s.sector, "stockCount" to s.stockCount,
                "medianPer" to s.medianPer, "medianPbr" to s.medianPbr, "medianRoe" to s.medianRoe,
                "medianOpMargin" to s.medianOperatingMargin, "medianDebtRatio" to s.medianDebtRatio,
            )
        }
        return objectMapper.writeValueAsString(mapOf("market" to market.name, "date" to date, "sectors" to sectors))
    }

    @Suppress("UNCHECKED_CAST")
    private fun evaluatePortfolio(input: Map<String, Any?>): String {
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
                "roe" to weightedAvg { fundamentals[it]?.roe },
                "debtRatio" to weightedAvg { fundamentals[it]?.debtRatio },
            ),
            "warnings" to warnings,
        )
        return objectMapper.writeValueAsString(result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun asList(v: Any?): List<String>? = v as? List<String>

    private fun toBd(v: Any?): BigDecimal? = when (v) {
        is Number -> BigDecimal(v.toDouble())
        else -> null
    }
}

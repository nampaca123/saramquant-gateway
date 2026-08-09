package me.saramquantgateway.feature.stock.service

import me.saramquantgateway.domain.enum.market.Benchmark
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.enum.stock.PricePeriod
import me.saramquantgateway.domain.store.LlmCacheStore
import me.saramquantgateway.domain.lake.FactorLakeDao
import me.saramquantgateway.domain.lake.FundamentalLakeDao
import me.saramquantgateway.domain.lake.IndicatorLakeDao
import me.saramquantgateway.domain.lake.PriceLakeDao
import me.saramquantgateway.domain.lake.RiskBadgeLakeDao
import me.saramquantgateway.domain.lake.SectorLakeDao
import me.saramquantgateway.domain.lake.StockLakeDao
import me.saramquantgateway.feature.stock.dto.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

@Service
class StockService(
    private val stockDao: StockLakeDao,
    private val priceDao: PriceLakeDao,
    private val indicatorDao: IndicatorLakeDao,
    private val fundamentalDao: FundamentalLakeDao,
    private val riskBadgeDao: RiskBadgeLakeDao,
    private val sectorAggDao: SectorLakeDao,
    private val factorDao: FactorLakeDao,
    private val llmCacheStore: LlmCacheStore,
) {

    fun getDetail(symbol: String, market: Market, lang: String): StockDetailResponse {
        val stock = stockDao.findBySymbolAndMarket(symbol, market)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")

        val prices = priceDao.findTop2ByStockId(stock.id, market)
        val latest = prices.firstOrNull()
        val prev = prices.getOrNull(1)

        val changePercent = if (latest != null && prev != null && prev.close.signum() != 0)
            latest.close.subtract(prev.close)
                .divide(prev.close, 6, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal(100))
                .toDouble()
        else null

        val indicator = indicatorDao.findLatestByStockId(stock.id)
        val fundamental = fundamentalDao.findLatestByStockId(stock.id)
        val badge = riskBadgeDao.findByStockId(stock.id)
        val sectorAgg = stock.sector?.let { sectorAggDao.findLatestByMarketAndSector(market, it) }
        val factor = factorDao.findLatestByStockId(stock.id)
        val llm = llmCacheStore.findStock(stock.id, LocalDate.now(), "summary", lang)

        return StockDetailResponse(
            header = StockHeader(
                stockId = stock.id,
                symbol = stock.symbol,
                name = stock.name,
                market = stock.market.name,
                sector = stock.sector,
                latestClose = latest?.close,
                priceChangePercent = changePercent,
                latestDate = latest?.date?.toString(),
            ),
            riskBadge = badge?.let {
                RiskBadgeDetail(
                    summaryTier = it.summaryTier,
                    date = it.date.toString(),
                    dimensions = invertDimensionScores(it.dimensions),
                )
            },
            indicators = indicator?.let {
                IndicatorSnapshot(
                    date = it.date.toString(),
                    rsi14 = it.rsi14, macd = it.macd, macdSignal = it.macdSignal, macdHist = it.macdHist,
                    stochK = it.stochK, stochD = it.stochD,
                    bbUpper = it.bbUpper, bbMiddle = it.bbMiddle, bbLower = it.bbLower,
                    adx14 = it.adx14, plusDi = it.plusDi, minusDi = it.minusDi,
                    atr14 = it.atr14,
                    sma20 = it.sma20, ema20 = it.ema20,
                    sar = it.sar,
                    obv = it.obv, vma20 = it.vma20,
                    beta = it.beta, alpha = it.alpha, sharpe = it.sharpe,
                )
            },
            fundamentals = fundamental?.let {
                FundamentalSnapshot(
                    date = it.date.toString(),
                    per = it.per, pbr = it.pbr, eps = it.eps, bps = it.bps,
                    roe = it.roe, debtRatio = it.debtRatio, operatingMargin = it.operatingMargin,
                )
            },
            sectorComparison = sectorAgg?.let {
                SectorComparisonSnapshot(
                    sector = it.sector,
                    stockCount = it.stockCount,
                    medianPer = it.medianPer, medianPbr = it.medianPbr, medianRoe = it.medianRoe,
                    medianOperatingMargin = it.medianOperatingMargin, medianDebtRatio = it.medianDebtRatio,
                )
            },
            factorExposures = factor?.let {
                FactorExposureSnapshot(
                    date = it.date.toString(),
                    sizeZ = it.sizeZ, valueZ = it.valueZ, momentumZ = it.momentumZ,
                    volatilityZ = it.volatilityZ, qualityZ = it.qualityZ, leverageZ = it.leverageZ,
                )
            },
            llmAnalysis = llm?.let {
                CachedLlmAnalysis(
                    preset = it.preset,
                    lang = it.lang,
                    analysis = it.analysis,
                    model = it.model,
                    createdAt = it.createdAt.toString(),
                )
            },
        )
    }

    fun getPrices(symbol: String, market: Market, period: PricePeriod): PriceSeriesResponse {
        val stock = stockDao.findBySymbolAndMarket(symbol, market)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")

        val today = LocalDate.now()
        val from = today.minusDays(period.tradingDays * 7L / 5 + 10)
        val all = priceDao.findByStockIdAndDateBetween(stock.id, market, from, today)
        val trimmed = all.take(period.tradingDays).reversed()

        return PriceSeriesResponse(
            symbol = stock.symbol,
            market = stock.market.name,
            period = period.name,
            prices = trimmed.map {
                PricePoint(
                    date = it.date.toString(),
                    open = it.open, high = it.high, low = it.low, close = it.close,
                    volume = it.volume,
                )
            },
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun invertDimensionScores(dimensions: Map<String, Any>): Map<String, Any> {
        val dims = dimensions["dims"] as? List<Map<String, Any>> ?: return dimensions
        val inverted = dims.map { dim ->
            val score = (dim["score"] as? Number)?.toDouble() ?: return@map dim
            val flipped = BigDecimal(100.0 - score).setScale(1, RoundingMode.HALF_UP).toDouble()
            dim.toMutableMap().apply { this["score"] = flipped }
        }
        return dimensions.toMutableMap().apply { this["dims"] = inverted }
    }

    fun getBenchmark(symbol: String, market: Market, period: PricePeriod): BenchmarkComparisonResponse {
        val stock = stockDao.findBySymbolAndMarket(symbol, market)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")

        val benchmark = Benchmark.forMarket(market)
        val today = LocalDate.now()
        val from = today.minusDays(period.tradingDays * 7L / 5 + 10)

        val stockPrices = priceDao.findByStockIdAndDateBetween(stock.id, market, from, today)
            .take(period.tradingDays).reversed()
        val benchPrices = priceDao.findBenchmarkByDateBetween(benchmark, from, today)
            .take(period.tradingDays).reversed()

        val stockByDate = stockPrices.associateBy { it.date }
        val benchByDate = benchPrices.associateBy { it.date }
        val overlapping = stockByDate.keys.intersect(benchByDate.keys).sorted()

        if (overlapping.isEmpty()) {
            return BenchmarkComparisonResponse(
                symbol = stock.symbol, benchmark = benchmark.name, period = period.name,
                stockSeries = emptyList(), benchmarkSeries = emptyList(),
            )
        }

        val stockBase = stockByDate[overlapping.first()]!!.close.toDouble()
        val benchBase = benchByDate[overlapping.first()]!!.close.toDouble()

        return BenchmarkComparisonResponse(
            symbol = stock.symbol,
            benchmark = benchmark.name,
            period = period.name,
            stockSeries = overlapping.map { d ->
                NormalizedPoint(d.toString(), stockByDate[d]!!.close.toDouble() / stockBase * 100.0)
            },
            benchmarkSeries = overlapping.map { d ->
                NormalizedPoint(d.toString(), benchByDate[d]!!.close.toDouble() / benchBase * 100.0)
            },
        )
    }
}

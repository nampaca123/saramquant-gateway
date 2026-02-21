package me.saramquantgateway.feature.stock.service

import me.saramquantgateway.domain.enum.market.Benchmark
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.enum.stock.PricePeriod
import me.saramquantgateway.domain.repository.llm.StockLlmAnalysisRepository
import me.saramquantgateway.domain.repository.factor.FactorExposureRepository
import me.saramquantgateway.domain.repository.fundamental.StockFundamentalRepository
import me.saramquantgateway.domain.repository.indicator.StockIndicatorRepository
import me.saramquantgateway.domain.repository.market.BenchmarkDailyPriceRepository
import me.saramquantgateway.domain.repository.market.SectorAggregateRepository
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
import me.saramquantgateway.domain.repository.stock.DailyPriceRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.feature.stock.dto.*
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@Service
class StockService(
    private val stockRepo: StockRepository,
    private val dailyPriceRepo: DailyPriceRepository,
    private val indicatorRepo: StockIndicatorRepository,
    private val fundamentalRepo: StockFundamentalRepository,
    private val riskBadgeRepo: RiskBadgeRepository,
    private val sectorAggRepo: SectorAggregateRepository,
    private val factorRepo: FactorExposureRepository,
    private val llmRepo: StockLlmAnalysisRepository,
    private val benchmarkPriceRepo: BenchmarkDailyPriceRepository,
) {

    fun getDetail(symbol: String, market: Market, lang: String): StockDetailResponse {
        val stock = stockRepo.findBySymbolAndMarketAndIsActiveTrue(symbol, market)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")

        val prices = dailyPriceRepo.findTop2ByStockIdOrderByDateDesc(stock.id)
        val latest = prices.firstOrNull()
        val prev = prices.getOrNull(1)

        val changePercent = if (latest != null && prev != null && prev.close.signum() != 0)
            latest.close.subtract(prev.close)
                .divide(prev.close, 6, java.math.RoundingMode.HALF_UP)
                .multiply(java.math.BigDecimal(100))
                .toDouble()
        else null

        val indicator = indicatorRepo.findTop1ByStockIdOrderByDateDesc(stock.id)
        val fundamental = fundamentalRepo.findTop1ByStockIdOrderByDateDesc(stock.id)
        val badge = riskBadgeRepo.findByStockId(stock.id)
        val sectorAgg = stock.sector?.let { sectorAggRepo.findTop1ByMarketAndSectorOrderByDateDesc(market, it) }
        val factor = factorRepo.findTop1ByStockIdOrderByDateDesc(stock.id)
        val llm = llmRepo.findByStockIdAndDateAndPresetAndLang(stock.id, LocalDate.now(), "summary", lang)

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
                    dimensions = it.dimensions,
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
        val stock = stockRepo.findBySymbolAndMarketAndIsActiveTrue(symbol, market)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")

        val today = LocalDate.now()
        val from = today.minusDays(period.tradingDays * 7L / 5 + 10)
        val all = dailyPriceRepo.findByStockIdAndDateBetweenOrderByDateDesc(stock.id, from, today)
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

    fun getBenchmark(symbol: String, market: Market, period: PricePeriod): BenchmarkComparisonResponse {
        val stock = stockRepo.findBySymbolAndMarketAndIsActiveTrue(symbol, market)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")

        val benchmark = Benchmark.forMarket(market)
        val today = LocalDate.now()
        val from = today.minusDays(period.tradingDays * 7L / 5 + 10)

        val stockPrices = dailyPriceRepo.findByStockIdAndDateBetweenOrderByDateDesc(stock.id, from, today)
            .take(period.tradingDays).reversed()
        val benchPrices = benchmarkPriceRepo.findByBenchmarkAndDateBetweenOrderByDateDesc(benchmark, from, today)
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

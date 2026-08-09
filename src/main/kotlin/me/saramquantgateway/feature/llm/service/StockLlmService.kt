package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.domain.entity.stock.DailyPrice
import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.fundamental.ReportType
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.lake.FactorLakeDao
import me.saramquantgateway.domain.lake.FundamentalLakeDao
import me.saramquantgateway.domain.lake.IndicatorLakeDao
import me.saramquantgateway.domain.lake.MarketRefLakeDao
import me.saramquantgateway.domain.lake.PriceLakeDao
import me.saramquantgateway.domain.lake.RiskBadgeLakeDao
import me.saramquantgateway.domain.lake.SectorLakeDao
import me.saramquantgateway.domain.lake.StockLakeDao
import me.saramquantgateway.domain.document.LlmAnalysisDoc
import me.saramquantgateway.domain.store.LlmCacheStore
import me.saramquantgateway.feature.llm.dto.LlmAnalysisResponse
import me.saramquantgateway.feature.stock.dto.*
import me.saramquantgateway.infra.llm.config.LlmProperties
import me.saramquantgateway.infra.llm.lib.LlmRouter
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@Service
class StockLlmService(
    private val cacheStore: LlmCacheStore,
    private val stockDao: StockLakeDao,
    private val priceDao: PriceLakeDao,
    private val indicatorDao: IndicatorLakeDao,
    private val fundamentalDao: FundamentalLakeDao,
    private val badgeDao: RiskBadgeLakeDao,
    private val sectorAggDao: SectorLakeDao,
    private val factorDao: FactorLakeDao,
    private val marketRefDao: MarketRefLakeDao,
    private val promptBuilder: PromptBuilder,
    private val llmRouter: LlmRouter,
    private val props: LlmProperties,
    @param:Qualifier("llmExecutor") private val llmExecutor: Executor,
) {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<String>>()

    fun getCached(symbol: String, market: Market, preset: String, lang: String): LlmAnalysisResponse? {
        val stock = stockDao.findBySymbolAndMarket(symbol, market) ?: return null
        val cached = cacheStore.findStock(stock.id, LocalDate.now(), preset, lang)
            ?: return null
        return toResponse(cached, true)
    }

    fun analyze(symbol: String, market: Market, preset: String, lang: String): LlmAnalysisResponse {
        val stock = stockDao.findBySymbolAndMarket(symbol, market)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")
        val today = LocalDate.now()

        cacheStore.findStock(stock.id, today, preset, lang)?.let {
            return toResponse(it, true)
        }

        val cacheKey = "${stock.id}:$today:$preset:$lang"
        val future = inFlight.computeIfAbsent(cacheKey) {
            CompletableFuture.supplyAsync({ generateAndCache(stock, today, preset, lang) }, llmExecutor)
        }

        try {
            val analysis = future.get(props.totalTimeout.toSeconds() + 5, TimeUnit.SECONDS)
            return LlmAnalysisResponse(analysis, props.stockModel, false, LlmAnalysisResponse.disclaimer(lang))
        } finally {
            inFlight.remove(cacheKey)
        }
    }

    private fun generateAndCache(stock: Stock, today: LocalDate, preset: String, lang: String): String {
        val data = buildContextData(stock)
        val (system, user) = promptBuilder.buildStockPrompt(data, preset, lang)
        val result = llmRouter.complete(props.stockModel, system, user)

        cacheStore.saveStock(
            LlmAnalysisDoc(
                targetId = stock.id, date = today, preset = preset, lang = lang,
                analysis = result, model = props.stockModel,
            )
        )
        return result
    }

    private fun buildContextData(stock: Stock): StockContextData {
        val today = LocalDate.now()
        val yearAgo = today.minusYears(1)
        val history = priceDao.findByStockIdAndDateBetween(stock.id, stock.market, yearAgo, today)
        val latest = history.firstOrNull()
        val prev = history.getOrNull(1)

        val changePercent = pctReturn(prev, latest)
        val weekReturn = periodReturn(latest, history, 7)
        val monthReturn = periodReturn(latest, history, 30)
        val threeMonthReturn = periodReturn(latest, history, 90)
        val week52High = history.maxOfOrNull { it.high }
        val week52Low = history.minOfOrNull { it.low }

        val financials = fundamentalDao.findFinancialsByStockId(stock.id, stock.market)
        val latestFY = financials.firstOrNull { it.reportType == ReportType.FY }
        val prevFY = financials.filter { it.reportType == ReportType.FY }.getOrNull(1)

        val marketCap = if (latest != null && latestFY?.sharesOutstanding != null)
            latest.close.multiply(BigDecimal(latestFY.sharesOutstanding)) else null

        val indicator = indicatorDao.findLatestByStockId(stock.id)
        val fundamental = fundamentalDao.findLatestByStockId(stock.id)
        val badge = badgeDao.findByStockId(stock.id)
        val sectorAgg = stock.sector?.let { sectorAggDao.findLatestByMarketAndSector(stock.market, it) }
        val factor = factorDao.findLatestByStockId(stock.id)
        val country = Country.forMarket(stock.market)
        val riskFreeRate = marketRefDao.findLatestRiskFreeRate(country, Maturity.Y1)?.rate

        return StockContextData(
            name = stock.name,
            symbol = stock.symbol,
            market = stock.market.name,
            sector = stock.sector,
            close = latest?.close,
            priceChange = changePercent,
            dataDate = latest?.date?.toString(),
            weekReturn = weekReturn,
            monthReturn = monthReturn,
            threeMonthReturn = threeMonthReturn,
            week52High = week52High,
            week52Low = week52Low,
            marketCap = marketCap,
            badge = badge?.dimensions,
            summaryTier = badge?.summaryTier,
            indicator = indicator?.let {
                IndicatorSnapshot(
                    date = it.date.toString(),
                    rsi14 = it.rsi14, macd = it.macd, macdSignal = it.macdSignal, macdHist = it.macdHist,
                    stochK = it.stochK, stochD = it.stochD,
                    bbUpper = it.bbUpper, bbMiddle = it.bbMiddle, bbLower = it.bbLower,
                    adx14 = it.adx14, plusDi = it.plusDi, minusDi = it.minusDi,
                    atr14 = it.atr14, sma20 = it.sma20, ema20 = it.ema20, sar = it.sar,
                    obv = it.obv, vma20 = it.vma20,
                    beta = it.beta, alpha = it.alpha, sharpe = it.sharpe,
                )
            },
            fundamental = fundamental?.let {
                FundamentalSnapshot(
                    date = it.date.toString(),
                    per = it.per, pbr = it.pbr, eps = it.eps, bps = it.bps,
                    roe = it.roe, debtRatio = it.debtRatio, operatingMargin = it.operatingMargin,
                )
            },
            sectorComparison = sectorAgg?.let {
                SectorComparisonSnapshot(
                    sector = it.sector, stockCount = it.stockCount,
                    medianPer = it.medianPer, medianPbr = it.medianPbr, medianRoe = it.medianRoe,
                    medianOperatingMargin = it.medianOperatingMargin, medianDebtRatio = it.medianDebtRatio,
                )
            },
            factorExposure = factor?.let {
                FactorExposureSnapshot(
                    date = it.date.toString(),
                    sizeZ = it.sizeZ, valueZ = it.valueZ, momentumZ = it.momentumZ,
                    volatilityZ = it.volatilityZ, qualityZ = it.qualityZ, leverageZ = it.leverageZ,
                )
            },
            financialStatement = latestFY?.let { fy ->
                FinancialStatementSnapshot(
                    fiscalYear = fy.fiscalYear,
                    revenue = fy.revenue,
                    operatingIncome = fy.operatingIncome,
                    netIncome = fy.netIncome,
                    totalAssets = fy.totalAssets,
                    totalEquity = fy.totalEquity,
                    revenueGrowthPct = growthPct(prevFY?.revenue, fy.revenue),
                    netIncomeGrowthPct = growthPct(prevFY?.netIncome, fy.netIncome),
                )
            },
            riskFreeRate = riskFreeRate,
        )
    }

    private fun pctReturn(from: DailyPrice?, to: DailyPrice?): Double? {
        if (from == null || to == null || from.close.signum() == 0) return null
        return to.close.subtract(from.close)
            .divide(from.close, 6, RoundingMode.HALF_UP)
            .multiply(BD_100).toDouble()
    }

    private fun periodReturn(latest: DailyPrice?, history: List<DailyPrice>, daysAgo: Long): Double? {
        if (latest == null) return null
        val target = history.firstOrNull { it.date <= latest.date.minusDays(daysAgo) } ?: return null
        return pctReturn(target, latest)
    }

    private fun growthPct(prev: BigDecimal?, curr: BigDecimal?): Double? {
        if (prev == null || curr == null || prev.signum() == 0) return null
        return curr.subtract(prev).divide(prev.abs(), 6, RoundingMode.HALF_UP)
            .multiply(BD_100).toDouble()
    }

    companion object {
        private val BD_100 = BigDecimal(100)
    }

    private fun toResponse(doc: LlmAnalysisDoc, cached: Boolean): LlmAnalysisResponse =
        LlmAnalysisResponse(
            analysis = doc.analysis,
            model = doc.model,
            cached = cached,
            disclaimer = LlmAnalysisResponse.disclaimer(doc.lang),
        )
}

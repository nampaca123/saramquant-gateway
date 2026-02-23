package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.repository.llm.StockLlmAnalysisRepository
import me.saramquantgateway.domain.repository.factor.FactorExposureRepository
import me.saramquantgateway.domain.repository.fundamental.StockFundamentalRepository
import me.saramquantgateway.domain.repository.indicator.StockIndicatorRepository
import me.saramquantgateway.domain.repository.market.RiskFreeRateRepository
import me.saramquantgateway.domain.repository.market.SectorAggregateRepository
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
import me.saramquantgateway.domain.repository.stock.DailyPriceRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.domain.entity.llm.StockLlmAnalysis
import me.saramquantgateway.feature.llm.dto.LlmAnalysisResponse
import me.saramquantgateway.feature.stock.dto.*
import me.saramquantgateway.infra.llm.config.LlmProperties
import me.saramquantgateway.infra.llm.lib.LlmRouter
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

@Service
class StockLlmService(
    private val analysisRepo: StockLlmAnalysisRepository,
    private val stockRepo: StockRepository,
    private val priceRepo: DailyPriceRepository,
    private val indicatorRepo: StockIndicatorRepository,
    private val fundamentalRepo: StockFundamentalRepository,
    private val badgeRepo: RiskBadgeRepository,
    private val sectorAggRepo: SectorAggregateRepository,
    private val factorRepo: FactorExposureRepository,
    private val riskFreeRateRepo: RiskFreeRateRepository,
    private val promptBuilder: PromptBuilder,
    private val llmRouter: LlmRouter,
    private val props: LlmProperties,
) {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<String>>()

    fun getCached(symbol: String, market: Market, preset: String, lang: String): LlmAnalysisResponse? {
        val stock = stockRepo.findBySymbolAndMarketAndIsActiveTrue(symbol, market) ?: return null
        val cached = analysisRepo.findByStockIdAndDateAndPresetAndLang(stock.id, LocalDate.now(), preset, lang)
            ?: return null
        return toResponse(cached, true)
    }

    fun analyze(symbol: String, market: Market, preset: String, lang: String): LlmAnalysisResponse {
        val stock = stockRepo.findBySymbolAndMarketAndIsActiveTrue(symbol, market)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Stock not found")
        val today = LocalDate.now()

        analysisRepo.findByStockIdAndDateAndPresetAndLang(stock.id, today, preset, lang)?.let {
            return toResponse(it, true)
        }

        val cacheKey = "${stock.id}:$today:$preset:$lang"
        val future = inFlight.computeIfAbsent(cacheKey) {
            CompletableFuture.supplyAsync { generateAndCache(stock, today, preset, lang) }
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

        analysisRepo.save(
            StockLlmAnalysis(
                stockId = stock.id, date = today, preset = preset, lang = lang,
                analysis = result, model = props.stockModel,
            )
        )
        return result
    }

    private fun buildContextData(stock: Stock): StockContextData {
        val prices = priceRepo.findTop2ByStockIdOrderByDateDesc(stock.id)
        val latest = prices.firstOrNull()
        val prev = prices.getOrNull(1)
        val changePercent = if (latest != null && prev != null && prev.close.signum() != 0)
            latest.close.subtract(prev.close)
                .divide(prev.close, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
                .toDouble()
        else null

        val indicator = indicatorRepo.findTop1ByStockIdOrderByDateDesc(stock.id)
        val fundamental = fundamentalRepo.findTop1ByStockIdOrderByDateDesc(stock.id)
        val badge = badgeRepo.findByStockId(stock.id)
        val sectorAgg = stock.sector?.let { sectorAggRepo.findTop1ByMarketAndSectorOrderByDateDesc(stock.market, it) }
        val factor = factorRepo.findTop1ByStockIdOrderByDateDesc(stock.id)
        val country = Country.forMarket(stock.market)
        val riskFreeRate = riskFreeRateRepo.findTop1ByCountryAndMaturityOrderByDateDesc(country, Maturity.Y1)?.rate

        return StockContextData(
            name = stock.name,
            symbol = stock.symbol,
            market = stock.market.name,
            sector = stock.sector,
            close = latest?.close,
            priceChange = changePercent,
            dataDate = latest?.date?.toString(),
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
            riskFreeRate = riskFreeRate,
        )
    }

    private fun toResponse(entity: StockLlmAnalysis, cached: Boolean): LlmAnalysisResponse =
        LlmAnalysisResponse(
            analysis = entity.analysis,
            model = entity.model,
            cached = cached,
            disclaimer = LlmAnalysisResponse.disclaimer(entity.lang),
        )
}

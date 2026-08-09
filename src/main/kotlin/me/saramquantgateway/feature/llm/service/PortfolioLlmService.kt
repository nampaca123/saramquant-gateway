package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.domain.document.HoldingEntry
import me.saramquantgateway.domain.document.LlmAnalysisDoc
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.domain.lake.FundamentalLakeDao
import me.saramquantgateway.domain.lake.IndicatorLakeDao
import me.saramquantgateway.domain.lake.MarketRefLakeDao
import me.saramquantgateway.domain.lake.RiskBadgeLakeDao
import me.saramquantgateway.domain.lake.SectorLakeDao
import me.saramquantgateway.domain.lake.StockLakeDao
import me.saramquantgateway.domain.store.LlmCacheStore
import me.saramquantgateway.feature.llm.dto.LlmAnalysisResponse
import me.saramquantgateway.feature.portfolio.service.CalcPortfolioRequestBuilder
import me.saramquantgateway.feature.portfolio.service.PortfolioService
import me.saramquantgateway.infra.llm.config.LlmProperties
import me.saramquantgateway.infra.llm.lib.LlmRouter
import me.saramquantgateway.infra.connection.CalcServerClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

@Service
class PortfolioLlmService(
    private val portfolioService: PortfolioService,
    private val stockDao: StockLakeDao,
    private val indicatorDao: IndicatorLakeDao,
    private val fundamentalDao: FundamentalLakeDao,
    private val badgeDao: RiskBadgeLakeDao,
    private val sectorAggDao: SectorLakeDao,
    private val marketRefDao: MarketRefLakeDao,
    private val cacheStore: LlmCacheStore,
    private val calcClient: CalcServerClient,
    private val calcRequestBuilder: CalcPortfolioRequestBuilder,
    private val promptBuilder: PromptBuilder,
    private val llmRouter: LlmRouter,
    private val props: LlmProperties,
    @param:Qualifier("llmExecutor") private val llmExecutor: Executor,
) {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<String>>()

    fun analyze(portfolioId: Long, userId: UUID, preset: String, lang: String): LlmAnalysisResponse {
        val portfolio = portfolioService.verifyOwnership(portfolioId, userId)
        val holdings = portfolio.holdings.toList()
        if (holdings.isEmpty()) {
            return LlmAnalysisResponse(
                analysis = if (lang == "en") "No holdings in this portfolio." else "포트폴리오에 보유 종목이 없습니다.",
                model = props.portfolioModel, cached = false,
                disclaimer = LlmAnalysisResponse.disclaimer(lang),
            )
        }

        val today = LocalDate.now()

        cacheStore.findPortfolio(portfolioId, today, preset, lang)?.let {
            return LlmAnalysisResponse(it.analysis, it.model, true, LlmAnalysisResponse.disclaimer(lang))
        }

        val cacheKey = "$portfolioId:$today:$preset:$lang"
        val future = inFlight.computeIfAbsent(cacheKey) {
            CompletableFuture.supplyAsync(
                { generateAndCache(portfolioId, portfolio.marketGroup, holdings, today, preset, lang) },
                llmExecutor,
            )
        }

        try {
            val analysis = future.get(props.totalTimeout.toSeconds() + 5, TimeUnit.SECONDS)
            return LlmAnalysisResponse(analysis, props.portfolioModel, false, LlmAnalysisResponse.disclaimer(lang))
        } finally {
            inFlight.remove(cacheKey)
        }
    }

    private fun generateAndCache(
        portfolioId: Long,
        marketGroup: String,
        holdings: List<HoldingEntry>,
        today: LocalDate,
        preset: String,
        lang: String,
    ): String {
        val data = buildContextData(marketGroup, holdings, preset, lang)
        val (system, user) = promptBuilder.buildPortfolioPrompt(data, preset, lang)
        val result = llmRouter.complete(props.portfolioModel, system, user)

        cacheStore.savePortfolio(
            LlmAnalysisDoc(
                targetId = portfolioId, date = today, preset = preset, lang = lang,
                analysis = result, model = props.portfolioModel,
            )
        )
        return result
    }

    private fun buildContextData(
        marketGroup: String,
        holdings: List<HoldingEntry>,
        preset: String,
        lang: String,
    ): PortfolioContextData {
        val stockIds = holdings.map { it.stockId }
        val stockMap = stockDao.findByIds(stockIds).associateBy { it.id }
        val indicatorMap = indicatorDao.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val fundamentalMap = fundamentalDao.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val badgeMap = badgeDao.findByStockIds(stockIds).associateBy { it.stockId }

        val totalValue = holdings.sumOf { it.shares.multiply(it.avgPrice).toDouble() }
        val needFundamentals = preset in setOf("financial_weakness", "aggressive")

        val holdingContexts = holdings.mapNotNull { h ->
            val stock = stockMap[h.stockId] ?: return@mapNotNull null
            val ind = indicatorMap[h.stockId]
            val fund = fundamentalMap[h.stockId]
            val badge = badgeMap[h.stockId]
            val weight = if (totalValue > 0) h.shares.multiply(h.avgPrice).toDouble() / totalValue * 100.0 else 0.0

            val sectorAgg = if (needFundamentals && stock.sector != null)
                sectorAggDao.findLatestByMarketAndSector(stock.market, stock.sector)
            else null

            HoldingContext(
                name = stock.name, symbol = stock.symbol, sector = stock.sector,
                weightPct = weight, summaryTier = badge?.summaryTier,
                beta = ind?.beta, sharpe = ind?.sharpe,
                debtRatio = if (needFundamentals) fund?.debtRatio else null,
                roe = if (needFundamentals) fund?.roe else null,
                operatingMargin = if (needFundamentals) fund?.operatingMargin else null,
                sectorDebtRatio = sectorAgg?.medianDebtRatio,
                sectorRoe = sectorAgg?.medianRoe,
                sectorOpMargin = sectorAgg?.medianOperatingMargin,
            )
        }

        val analysis = calcClient.post(
            "/internal/portfolios/full-analysis",
            calcRequestBuilder.build(marketGroup, holdings),
        )

        val firstStock = stockMap.values.firstOrNull()
        val country = firstStock?.let { Country.forMarket(it.market) } ?: Country.KR
        val riskFreeRate = marketRefDao.findLatestRiskFreeRate(country, Maturity.Y1)?.rate
        val benchmark = if (country == Country.KR) "KOSPI" else "S&P500"

        @Suppress("UNCHECKED_CAST")
        return PortfolioContextData(
            holdings = holdingContexts,
            riskScore = analysis?.get("risk_score") as? Map<String, Any?>,
            riskDecomp = analysis?.get("risk_decomposition") as? Map<String, Any?>,
            diversification = analysis?.get("diversification") as? Map<String, Any?>,
            riskFreeRate = riskFreeRate,
            benchmark = benchmark,
        )
    }

}

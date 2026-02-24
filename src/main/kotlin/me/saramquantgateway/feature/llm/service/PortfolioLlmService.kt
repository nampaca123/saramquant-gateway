package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.domain.entity.llm.PortfolioLlmAnalysis
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.domain.repository.fundamental.StockFundamentalRepository
import me.saramquantgateway.domain.repository.indicator.StockIndicatorRepository
import me.saramquantgateway.domain.repository.llm.PortfolioLlmAnalysisRepository
import me.saramquantgateway.domain.repository.market.RiskFreeRateRepository
import me.saramquantgateway.domain.repository.market.SectorAggregateRepository
import me.saramquantgateway.domain.repository.portfolio.PortfolioHoldingRepository
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.feature.llm.dto.LlmAnalysisResponse
import me.saramquantgateway.feature.portfolio.service.PortfolioService
import me.saramquantgateway.infra.llm.config.LlmProperties
import me.saramquantgateway.infra.llm.lib.LlmRouter
import me.saramquantgateway.infra.connection.CalcServerClient
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.dao.DataIntegrityViolationException
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
    private val holdingRepo: PortfolioHoldingRepository,
    private val stockRepo: StockRepository,
    private val indicatorRepo: StockIndicatorRepository,
    private val fundamentalRepo: StockFundamentalRepository,
    private val badgeRepo: RiskBadgeRepository,
    private val sectorAggRepo: SectorAggregateRepository,
    private val riskFreeRateRepo: RiskFreeRateRepository,
    private val analysisRepo: PortfolioLlmAnalysisRepository,
    private val calcClient: CalcServerClient,
    private val promptBuilder: PromptBuilder,
    private val llmRouter: LlmRouter,
    private val props: LlmProperties,
    @Qualifier("llmExecutor") private val llmExecutor: Executor,
) {
    private val inFlight = ConcurrentHashMap<String, CompletableFuture<String>>()

    fun analyze(portfolioId: Long, userId: UUID, preset: String, lang: String): LlmAnalysisResponse {
        val portfolio = portfolioService.verifyOwnership(portfolioId, userId)
        val holdings = holdingRepo.findByPortfolioId(portfolioId)
        if (holdings.isEmpty()) {
            return LlmAnalysisResponse(
                analysis = if (lang == "en") "No holdings in this portfolio." else "포트폴리오에 보유 종목이 없습니다.",
                model = props.portfolioModel, cached = false,
                disclaimer = LlmAnalysisResponse.disclaimer(lang),
            )
        }

        val today = LocalDate.now()

        analysisRepo.findByPortfolioIdAndDateAndPresetAndLang(portfolioId, today, preset, lang)?.let {
            return LlmAnalysisResponse(it.analysis, it.model, true, LlmAnalysisResponse.disclaimer(lang))
        }

        val cacheKey = "$portfolioId:$today:$preset:$lang"
        val future = inFlight.computeIfAbsent(cacheKey) {
            CompletableFuture.supplyAsync({ generateAndCache(portfolioId, holdings, today, preset, lang) }, llmExecutor)
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
        holdings: List<me.saramquantgateway.domain.entity.portfolio.PortfolioHolding>,
        today: LocalDate,
        preset: String,
        lang: String,
    ): String {
        val data = buildContextData(portfolioId, holdings, preset, lang)
        val (system, user) = promptBuilder.buildPortfolioPrompt(data, preset, lang)
        val result = llmRouter.complete(props.portfolioModel, system, user)

        try {
            analysisRepo.save(
                PortfolioLlmAnalysis(
                    portfolioId = portfolioId, date = today, preset = preset, lang = lang,
                    analysis = result, model = props.portfolioModel,
                )
            )
        } catch (_: DataIntegrityViolationException) {
            // race condition: 동시 요청이 먼저 INSERT 완료한 경우 → 무시하고 결과만 반환
        }
        return result
    }

    private fun buildContextData(
        portfolioId: Long,
        holdings: List<me.saramquantgateway.domain.entity.portfolio.PortfolioHolding>,
        preset: String,
        lang: String,
    ): PortfolioContextData {
        val stockIds = holdings.map { it.stockId }
        val stockMap = stockRepo.findByIdIn(stockIds).associateBy { it.id }
        val indicatorMap = indicatorRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val fundamentalMap = fundamentalRepo.findLatestByStockIds(stockIds).associateBy { it.stockId }
        val badgeMap = badgeRepo.findByStockIdIn(stockIds).associateBy { it.stockId }

        val totalValue = holdings.sumOf { it.shares.multiply(it.avgPrice).toDouble() }
        val needFundamentals = preset in setOf("financial_weakness", "aggressive")

        val holdingContexts = holdings.mapNotNull { h ->
            val stock = stockMap[h.stockId] ?: return@mapNotNull null
            val ind = indicatorMap[h.stockId]
            val fund = fundamentalMap[h.stockId]
            val badge = badgeMap[h.stockId]
            val weight = if (totalValue > 0) h.shares.multiply(h.avgPrice).toDouble() / totalValue * 100.0 else 0.0

            val sectorAgg = if (needFundamentals && stock.sector != null)
                sectorAggRepo.findTop1ByMarketAndSectorOrderByDateDesc(stock.market, stock.sector)
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

        val analysis = calcClient.post("/internal/portfolios/full-analysis", mapOf("portfolio_id" to portfolioId))

        val firstStock = stockMap.values.firstOrNull()
        val country = firstStock?.let { Country.forMarket(it.market) } ?: Country.KR
        val riskFreeRate = riskFreeRateRepo.findTop1ByCountryAndMaturityOrderByDateDesc(country, Maturity.Y1)?.rate
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

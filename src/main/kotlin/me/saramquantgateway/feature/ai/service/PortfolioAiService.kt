package me.saramquantgateway.feature.ai.service

import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.domain.repository.fundamental.StockFundamentalRepository
import me.saramquantgateway.domain.repository.indicator.StockIndicatorRepository
import me.saramquantgateway.domain.repository.market.RiskFreeRateRepository
import me.saramquantgateway.domain.repository.market.SectorAggregateRepository
import me.saramquantgateway.domain.repository.portfolio.PortfolioHoldingRepository
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
import me.saramquantgateway.domain.repository.stock.StockRepository
import me.saramquantgateway.feature.ai.dto.AiAnalysisResponse
import me.saramquantgateway.feature.portfolio.service.PortfolioService
import me.saramquantgateway.infra.ai.config.AiProperties
import me.saramquantgateway.infra.ai.lib.LlmRouter
import me.saramquantgateway.infra.connection.CalcServerClient
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class PortfolioAiService(
    private val portfolioService: PortfolioService,
    private val holdingRepo: PortfolioHoldingRepository,
    private val stockRepo: StockRepository,
    private val indicatorRepo: StockIndicatorRepository,
    private val fundamentalRepo: StockFundamentalRepository,
    private val badgeRepo: RiskBadgeRepository,
    private val sectorAggRepo: SectorAggregateRepository,
    private val riskFreeRateRepo: RiskFreeRateRepository,
    private val calcClient: CalcServerClient,
    private val promptBuilder: PromptBuilder,
    private val llmRouter: LlmRouter,
    private val props: AiProperties,
) {

    fun analyze(portfolioId: Long, userId: UUID, preset: String, lang: String): AiAnalysisResponse {
        val portfolio = portfolioService.verifyOwnership(portfolioId, userId)
        val holdings = holdingRepo.findByPortfolioId(portfolioId)
        if (holdings.isEmpty()) {
            return AiAnalysisResponse(
                analysis = if (lang == "en") "No holdings in this portfolio." else "포트폴리오에 보유 종목이 없습니다.",
                model = props.portfolioModel, cached = false,
                disclaimer = AiAnalysisResponse.disclaimer(lang),
            )
        }

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

        val riskScore = calcClient.post("/internal/portfolios/risk-score", mapOf("portfolio_id" to portfolioId))
        val riskDecomp = calcClient.post("/internal/portfolios/risk", mapOf("portfolio_id" to portfolioId))
        val diversification = calcClient.post("/internal/portfolios/diversification", mapOf("portfolio_id" to portfolioId))

        val firstStock = stockMap.values.firstOrNull()
        val country = firstStock?.let { Country.forMarket(it.market) } ?: Country.KR
        val riskFreeRate = riskFreeRateRepo.findTop1ByCountryAndMaturityOrderByDateDesc(country, Maturity.Y1)?.rate
        val benchmark = if (country == Country.KR) "KOSPI" else "S&P500"

        @Suppress("UNCHECKED_CAST")
        val data = PortfolioContextData(
            holdings = holdingContexts,
            riskScore = riskScore as? Map<String, Any?>,
            riskDecomp = riskDecomp as? Map<String, Any?>,
            diversification = diversification as? Map<String, Any?>,
            riskFreeRate = riskFreeRate,
            benchmark = benchmark,
        )

        val (system, user) = promptBuilder.buildPortfolioPrompt(data, preset, lang)
        val result = llmRouter.complete(props.portfolioModel, system, user)

        return AiAnalysisResponse(
            analysis = result, model = props.portfolioModel,
            cached = false, disclaimer = AiAnalysisResponse.disclaimer(lang),
        )
    }
}

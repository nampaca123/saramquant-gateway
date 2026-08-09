package me.saramquantgateway.feature.simulation.service

import me.saramquantgateway.feature.portfolio.service.CalcPortfolioRequestBuilder
import me.saramquantgateway.feature.portfolio.service.PortfolioService
import me.saramquantgateway.infra.connection.CalcServerClient
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class SimulationService(
    private val calcClient: CalcServerClient,
    private val portfolioService: PortfolioService,
    private val requestBuilder: CalcPortfolioRequestBuilder,
) {
    fun runStockSimulation(symbol: String, params: Map<String, String>): Map<*, *>? =
        calcClient.get("/internal/stocks/$symbol/simulation", params)

    fun runPortfolioSimulation(portfolioId: Long, userId: UUID, params: Map<String, String>): Map<*, *>? {
        val portfolio = portfolioService.verifyOwnership(portfolioId, userId)
        val body = requestBuilder.build(portfolio.marketGroup, portfolio.holdings.toList())
        return calcClient.post("/internal/portfolios/simulation", body, params)
    }
}

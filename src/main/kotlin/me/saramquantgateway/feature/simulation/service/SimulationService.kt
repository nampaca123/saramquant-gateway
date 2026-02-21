package me.saramquantgateway.feature.simulation.service

import me.saramquantgateway.infra.connection.CalcServerClient
import org.springframework.stereotype.Service

@Service
class SimulationService(
    private val calcClient: CalcServerClient,
) {
    fun runStockSimulation(symbol: String, params: Map<String, String>): Map<*, *>? =
        calcClient.get("/internal/stocks/$symbol/simulation", params)

    fun runPortfolioSimulation(portfolioId: Long, params: Map<String, String>): Map<*, *>? =
        calcClient.post("/internal/portfolios/$portfolioId/simulation", params = params)
}

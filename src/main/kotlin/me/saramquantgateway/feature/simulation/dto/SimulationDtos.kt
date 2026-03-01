package me.saramquantgateway.feature.simulation.dto

data class StockSimulationRequest(
    val market: String,
    val days: Int = 60,
    val simulations: Int = 30000,
    val confidence: Double = 0.95,
    val lookback: Int = 252,
    val method: String = "gbm",
)

data class PortfolioSimulationRequest(
    val days: Int = 60,
    val simulations: Int = 30000,
    val confidence: Double = 0.95,
    val lookback: Int = 252,
    val method: String = "bootstrap",
)

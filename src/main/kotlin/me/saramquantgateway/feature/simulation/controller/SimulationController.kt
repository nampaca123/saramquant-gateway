package me.saramquantgateway.feature.simulation.controller

import me.saramquantgateway.feature.simulation.dto.PortfolioSimulationRequest
import me.saramquantgateway.feature.simulation.dto.StockSimulationRequest
import me.saramquantgateway.feature.simulation.service.SimulationService
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class SimulationController(
    private val simulationService: SimulationService,
) {
    @GetMapping("/api/stocks/{symbol}/simulation")
    fun stockSimulation(
        @PathVariable symbol: String,
        @ModelAttribute req: StockSimulationRequest,
    ): ResponseEntity<Any> {
        val params = mapOf(
            "market" to req.market,
            "days" to req.days.toString(),
            "simulations" to req.simulations.toString(),
            "confidence" to req.confidence.toString(),
            "lookback" to req.lookback.toString(),
            "method" to req.method,
        )
        val result = simulationService.runStockSimulation(symbol, params)
            ?: return ResponseEntity.status(502).body(mapOf("error" to "Calc server unavailable"))
        return ResponseEntity.ok(result)
    }

    @PostMapping("/api/portfolios/{id}/simulation")
    fun portfolioSimulation(
        @PathVariable id: Long,
        @ModelAttribute req: PortfolioSimulationRequest,
    ): ResponseEntity<Any> {
        val params = mapOf(
            "days" to req.days.toString(),
            "simulations" to req.simulations.toString(),
            "confidence" to req.confidence.toString(),
            "lookback" to req.lookback.toString(),
            "method" to req.method,
        )
        val result = simulationService.runPortfolioSimulation(id, currentUserId(), params)
            ?: return ResponseEntity.status(502).body(mapOf("error" to "Calc server unavailable"))
        return ResponseEntity.ok(result)
    }

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication!!.name)
}

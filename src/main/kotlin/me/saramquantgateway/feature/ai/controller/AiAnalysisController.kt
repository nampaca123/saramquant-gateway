package me.saramquantgateway.feature.ai.controller

import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.feature.ai.dto.*
import me.saramquantgateway.feature.ai.service.AiUsageService
import me.saramquantgateway.feature.ai.service.PortfolioAiService
import me.saramquantgateway.feature.ai.service.StockAiService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class AiAnalysisController(
    private val stockAiService: StockAiService,
    private val portfolioAiService: PortfolioAiService,
    private val usageService: AiUsageService,
) {

    @GetMapping("/api/stocks/{symbol}/ai-analysis")
    fun cachedAnalysis(
        @PathVariable symbol: String,
        @RequestParam market: Market,
        @RequestParam(defaultValue = "summary") preset: String,
        @RequestParam(defaultValue = "ko") lang: String,
    ): ResponseEntity<AiAnalysisResponse> {
        val result = stockAiService.getCached(symbol, market, preset, lang)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @PostMapping("/api/ai/stock-analysis")
    fun triggerStockAnalysis(@RequestBody req: StockAnalysisRequest): ResponseEntity<AiAnalysisResponse> {
        val userId = currentUserId()
        if (!usageService.isWithinLimit(userId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }
        usageService.checkAndIncrement(userId)
        val market = Market.valueOf(req.market)
        return ResponseEntity.ok(stockAiService.analyze(req.symbol, market, req.preset, req.lang))
    }

    @PostMapping("/api/ai/portfolio-analysis")
    fun triggerPortfolioAnalysis(@RequestBody req: PortfolioAnalysisRequest): ResponseEntity<AiAnalysisResponse> {
        val userId = currentUserId()
        if (!usageService.isWithinLimit(userId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }
        usageService.checkAndIncrement(userId)
        return ResponseEntity.ok(portfolioAiService.analyze(req.portfolioId, userId, req.preset, req.lang))
    }

    @GetMapping("/api/ai/usage")
    fun usage(): ResponseEntity<AiUsageResponse> =
        ResponseEntity.ok(usageService.remaining(currentUserId()))

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication!!.name)
}

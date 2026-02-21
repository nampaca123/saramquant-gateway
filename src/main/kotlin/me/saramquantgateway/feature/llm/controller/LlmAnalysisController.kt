package me.saramquantgateway.feature.llm.controller

import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.feature.llm.dto.*
import me.saramquantgateway.feature.llm.service.LlmUsageService
import me.saramquantgateway.feature.llm.service.PortfolioLlmService
import me.saramquantgateway.feature.llm.service.StockLlmService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class LlmAnalysisController(
    private val stockLlmService: StockLlmService,
    private val portfolioLlmService: PortfolioLlmService,
    private val usageService: LlmUsageService,
) {

    @GetMapping("/api/stocks/{symbol}/llm-analysis")
    fun cachedAnalysis(
        @PathVariable symbol: String,
        @RequestParam market: Market,
        @RequestParam(defaultValue = "summary") preset: String,
        @RequestParam(defaultValue = "ko") lang: String,
    ): ResponseEntity<LlmAnalysisResponse> {
        val result = stockLlmService.getCached(symbol, market, preset, lang)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(result)
    }

    @PostMapping("/api/llm/stock-analysis")
    fun triggerStockAnalysis(@RequestBody req: StockAnalysisRequest): ResponseEntity<LlmAnalysisResponse> {
        val userId = currentUserId()
        if (!usageService.isWithinLimit(userId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }
        usageService.checkAndIncrement(userId)
        val market = Market.valueOf(req.market)
        return ResponseEntity.ok(stockLlmService.analyze(req.symbol, market, req.preset, req.lang))
    }

    @PostMapping("/api/llm/portfolio-analysis")
    fun triggerPortfolioAnalysis(@RequestBody req: PortfolioAnalysisRequest): ResponseEntity<LlmAnalysisResponse> {
        val userId = currentUserId()
        if (!usageService.isWithinLimit(userId)) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).build()
        }
        usageService.checkAndIncrement(userId)
        return ResponseEntity.ok(portfolioLlmService.analyze(req.portfolioId, userId, req.preset, req.lang))
    }

    @GetMapping("/api/llm/usage")
    fun usage(): ResponseEntity<LlmUsageResponse> =
        ResponseEntity.ok(usageService.remaining(currentUserId()))

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication!!.name)
}

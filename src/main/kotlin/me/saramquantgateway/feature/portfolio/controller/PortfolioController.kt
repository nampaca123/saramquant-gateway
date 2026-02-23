package me.saramquantgateway.feature.portfolio.controller

import me.saramquantgateway.domain.repository.llm.PortfolioLlmAnalysisRepository
import me.saramquantgateway.feature.portfolio.dto.BuyRequest
import me.saramquantgateway.feature.portfolio.dto.SellRequest
import me.saramquantgateway.feature.portfolio.service.PortfolioService
import me.saramquantgateway.infra.connection.CalcServerClient
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/portfolios")
class PortfolioController(
    private val portfolioService: PortfolioService,
    private val calcClient: CalcServerClient,
    private val llmCacheRepo: PortfolioLlmAnalysisRepository,
) {

    @GetMapping
    fun list(): ResponseEntity<Any> {
        val userId = currentUserId()
        return ResponseEntity.ok(portfolioService.getPortfolios(userId))
    }

    @GetMapping("/{id}")
    fun detail(@PathVariable id: Long): ResponseEntity<Any> {
        val userId = currentUserId()
        return ResponseEntity.ok(portfolioService.getPortfolioDetail(id, userId))
    }

    @PostMapping("/{id}/holdings")
    fun buy(@PathVariable id: Long, @RequestBody req: BuyRequest): ResponseEntity<Any> {
        val userId = currentUserId()
        val result = portfolioService.buy(id, userId, req)
        return ResponseEntity.status(HttpStatus.CREATED).body(result)
    }

    @PatchMapping("/{id}/holdings/{hid}")
    fun sell(
        @PathVariable id: Long,
        @PathVariable hid: Long,
        @RequestBody req: SellRequest,
    ): ResponseEntity<Any> {
        val userId = currentUserId()
        portfolioService.sell(id, hid, userId, req)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{id}/holdings/{hid}")
    fun deleteHolding(@PathVariable id: Long, @PathVariable hid: Long): ResponseEntity<Any> {
        val userId = currentUserId()
        portfolioService.deleteHolding(id, hid, userId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/reset")
    fun reset(@PathVariable id: Long): ResponseEntity<Any> {
        val userId = currentUserId()
        portfolioService.reset(id, userId)
        return ResponseEntity.noContent().build()
    }

    @Suppress("UNCHECKED_CAST")
    @GetMapping("/{id}/analysis")
    fun analysis(@PathVariable id: Long): ResponseEntity<Any> {
        val userId = currentUserId()
        portfolioService.verifyOwnership(id, userId)

        val result = calcClient.post("/internal/portfolios/full-analysis", mapOf("portfolio_id" to id))
            ?: return ResponseEntity.ok(emptyMap<String, Any>())

        val mutable = (result as Map<String, Any?>).toMutableMap()
        mutable["risk_score"] = invertRiskScore(mutable["risk_score"] as? Map<*, *>)
        return ResponseEntity.ok(mutable)
    }

    @GetMapping("/price-lookup")
    fun priceLookup(
        @RequestParam("stock_id") stockId: Long,
        @RequestParam("date") date: String,
    ): ResponseEntity<Any> {
        currentUserId()
        val body = mapOf("stock_id" to stockId, "date" to date)
        val result = calcClient.post("/internal/portfolios/price-lookup", body)
            ?: return ResponseEntity.ok(mapOf("found" to false))
        return ResponseEntity.ok(result)
    }

    @GetMapping("/{id}/llm-history")
    fun llmHistory(@PathVariable id: Long): ResponseEntity<Any> {
        val userId = currentUserId()
        portfolioService.verifyOwnership(id, userId)
        val rows = llmCacheRepo.findByPortfolioIdOrderByCreatedAtDesc(id)
        return ResponseEntity.ok(rows.map { mapOf(
            "id" to it.id,
            "date" to it.date,
            "preset" to it.preset,
            "lang" to it.lang,
            "analysis" to it.analysis,
            "model" to it.model,
            "created_at" to it.createdAt,
        )})
    }

    @Suppress("UNCHECKED_CAST")
    private fun invertRiskScore(raw: Map<*, *>?): Map<*, *>? {
        if (raw == null) return null
        val score = (raw["score"] as? Number)?.toDouble() ?: return raw
        val inverted = java.math.BigDecimal(100.0 - score)
            .setScale(2, java.math.RoundingMode.HALF_UP).toDouble()
        return (raw as Map<String, Any?>).toMutableMap().apply { this["score"] = inverted }
    }

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication!!.name)
}

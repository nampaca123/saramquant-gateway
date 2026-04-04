package me.saramquantgateway.feature.recommendation.controller

import me.saramquantgateway.domain.repository.recommendation.PortfolioRecommendationRepository
import me.saramquantgateway.feature.llm.service.LlmUsageService
import me.saramquantgateway.feature.portfolio.service.PortfolioService
import me.saramquantgateway.feature.recommendation.dto.RecommendationHistoryItem
import me.saramquantgateway.feature.recommendation.dto.RecommendationRequest
import me.saramquantgateway.feature.recommendation.service.RecommendationAgentService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.UUID
import java.util.concurrent.Executor

@RestController
class RecommendationController(
    private val agentService: RecommendationAgentService,
    private val usageService: LlmUsageService,
    private val portfolioService: PortfolioService,
    private val recRepo: PortfolioRecommendationRepository,
    @Qualifier("llmExecutor") private val llmExecutor: Executor,
) {
    companion object {
        private val VALID_MARKET_GROUPS = setOf("KR", "US")
        private val VALID_LANGS = setOf("ko", "en")
        private const val RECOMMENDATION_COST = 3
    }

    @GetMapping("/api/llm/portfolio-recommendation", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun recommend(
        @RequestParam marketGroup: String,
        @RequestParam(defaultValue = "ko") lang: String,
        @RequestParam(required = false) message: String?,
    ): SseEmitter {
        val userId = currentUserId()
        val emitter = SseEmitter(150_000L)

        if (marketGroup !in VALID_MARKET_GROUPS) {
            emitter.send(SseEmitter.event().name("error")
                .data("""{"message":"Invalid marketGroup. Must be KR or US."}"""))
            emitter.complete()
            return emitter
        }

        if (!usageService.isWithinLimit(userId)) {
            emitter.send(SseEmitter.event().name("error")
                .data("""{"message":"Daily usage limit exceeded"}"""))
            emitter.complete()
            return emitter
        }
        repeat(RECOMMENDATION_COST) { usageService.checkAndIncrement(userId) }

        val portfolios = portfolioService.getPortfolios(userId)
        val portfolioSummary = portfolios.firstOrNull { it.marketGroup == marketGroup }
        if (portfolioSummary == null) {
            emitter.send(SseEmitter.event().name("error")
                .data("""{"message":"Portfolio not found for $marketGroup"}"""))
            emitter.complete()
            return emitter
        }

        val portfolio = portfolioService.getPortfolioDetail(portfolioSummary.id, userId)
        val effectiveLang = if (lang in VALID_LANGS) lang else "ko"
        val req = RecommendationRequest(marketGroup, effectiveLang, message)
        llmExecutor.execute { agentService.recommend(req, portfolio, userId, emitter) }
        return emitter
    }

    @GetMapping("/api/llm/recommendation-history")
    fun history(
        @RequestParam marketGroup: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int,
    ): ResponseEntity<List<RecommendationHistoryItem>> {
        val userId = currentUserId()
        val records = recRepo.findByUserIdAndMarketGroupOrderByCreatedAtDesc(
            userId, marketGroup, PageRequest.of(page, size),
        )
        val items = records.content.map {
            RecommendationHistoryItem(
                id = it.id,
                marketGroup = it.marketGroup,
                stocks = it.stocks,
                reasoning = it.reasoning,
                model = it.model,
                createdAt = it.createdAt.toString(),
            )
        }
        return ResponseEntity.ok(items)
    }

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication!!.name)
}

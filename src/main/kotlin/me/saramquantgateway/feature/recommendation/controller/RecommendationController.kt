package me.saramquantgateway.feature.recommendation.controller

import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.domain.enum.recommendation.RecommendationDirection
import me.saramquantgateway.domain.repository.recommendation.PortfolioRecommendationRepository
import me.saramquantgateway.domain.repository.user.UserProfileRepository
import me.saramquantgateway.feature.llm.service.LlmUsageService
import me.saramquantgateway.feature.portfolio.dto.PortfolioDetail
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
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executor

@RestController
class RecommendationController(
    private val agentService: RecommendationAgentService,
    private val usageService: LlmUsageService,
    private val portfolioService: PortfolioService,
    private val profileRepo: UserProfileRepository,
    private val recRepo: PortfolioRecommendationRepository,
    private val objectMapper: ObjectMapper,
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
        @RequestParam(defaultValue = "IMPROVE") direction: String,
    ): SseEmitter {
        val userId = currentUserId()
        val emitter = SseEmitter(-1L)

        if (marketGroup !in VALID_MARKET_GROUPS) {
            emitErrorAndComplete(emitter, "INVALID_PARAM", "Invalid marketGroup. Must be KR or US.")
            return emitter
        }

        val parsedDirection = try {
            RecommendationDirection.valueOf(direction)
        } catch (_: IllegalArgumentException) {
            emitErrorAndComplete(emitter, "INVALID_PARAM", "Invalid direction. Must be IMPROVE, CONSERVATIVE, or GROWTH.")
            return emitter
        }

        if (!usageService.checkAndIncrementBy(userId, RECOMMENDATION_COST)) {
            emitErrorAndComplete(emitter, "CREDIT_EXCEEDED", "Daily usage limit exceeded")
            return emitter
        }

        val portfolio = loadPortfolioOrEmpty(userId, marketGroup)
        val profile = profileRepo.findByUserId(userId)
        val effectiveLang = if (lang in VALID_LANGS) lang else "ko"
        val req = RecommendationRequest(marketGroup, effectiveLang, parsedDirection)

        llmExecutor.execute {
            val success = agentService.recommend(req, portfolio, userId, profile, emitter)
            if (!success) usageService.decrementBy(userId, RECOMMENDATION_COST)
        }
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

    private fun loadPortfolioOrEmpty(userId: UUID, marketGroup: String): PortfolioDetail {
        val portfolios = portfolioService.getPortfolios(userId)
        val summary = portfolios.firstOrNull { it.marketGroup == marketGroup }
            ?: return PortfolioDetail(
                id = 0, marketGroup = marketGroup, holdings = emptyList(),
                createdAt = Instant.now(),
            )
        return portfolioService.getPortfolioDetail(summary.id, userId)
    }

    private fun emitErrorAndComplete(emitter: SseEmitter, code: String, message: String) {
        emitter.send(SseEmitter.event().name("error")
            .data(objectMapper.writeValueAsString(mapOf("code" to code, "message" to message))))
        emitter.complete()
    }

    private fun currentUserId(): UUID =
        UUID.fromString(SecurityContextHolder.getContext().authentication!!.name)
}

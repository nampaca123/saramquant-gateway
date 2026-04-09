package me.saramquantgateway.feature.recommendation.dto

import me.saramquantgateway.domain.enum.recommendation.RecommendationDirection

data class RecommendationRequest(
    val marketGroup: String,
    val lang: String = "ko",
    val direction: RecommendationDirection = RecommendationDirection.IMPROVE,
)

data class RecommendedStock(
    val stockId: Long,
    val symbol: String,
    val name: String,
    val sector: String?,
    val allocationPercent: Double,
    val action: String,
    val reasoning: String,
)

data class RecommendationResponse(
    val stocks: List<RecommendedStock>,
    val overallReasoning: String,
    val currentAssessment: String?,
    val model: String,
    val toolCallCount: Int,
)

data class RecommendationHistoryItem(
    val id: Long,
    val marketGroup: String,
    val stocks: String,
    val reasoning: String,
    val model: String,
    val createdAt: String,
)

data class ProgressEvent(
    val step: String,
    val message: String,
)

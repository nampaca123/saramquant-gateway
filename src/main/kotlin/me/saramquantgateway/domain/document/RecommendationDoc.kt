package me.saramquantgateway.domain.document

import java.time.Instant
import java.util.UUID

// 포트폴리오 추천 이력 문서.
data class RecommendationDoc(
    val userId: UUID,
    val marketGroup: String,
    val riskTolerance: String,
    val lang: String = "ko",
    val stocks: String,
    val reasoning: String,
    val model: String,
    val createdAt: Instant = Instant.now(),
    val id: Long = createdAt.toEpochMilli(),
)

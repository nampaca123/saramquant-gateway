package me.saramquantgateway.domain.document

import java.time.Instant
import java.time.LocalDate

// 종목/포트폴리오 LLM 분석 캐시 문서. targetId는 stockId 또는 portfolioId.
data class LlmAnalysisDoc(
    val targetId: Long,
    val date: LocalDate,
    val preset: String,
    val lang: String = "ko",
    val analysis: String,
    val model: String,
    val createdAt: Instant = Instant.now(),
    val id: Long = createdAt.toEpochMilli(),
)

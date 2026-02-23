package me.saramquantgateway.domain.repository.llm

import me.saramquantgateway.domain.entity.llm.PortfolioLlmAnalysis
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate

interface PortfolioLlmAnalysisRepository : JpaRepository<PortfolioLlmAnalysis, Long> {

    fun findByPortfolioIdAndDateAndPresetAndLang(
        portfolioId: Long, date: LocalDate, preset: String, lang: String,
    ): PortfolioLlmAnalysis?

    fun findByPortfolioIdOrderByCreatedAtDesc(portfolioId: Long): List<PortfolioLlmAnalysis>

    fun deleteByPortfolioId(portfolioId: Long)

    fun deleteByCreatedAtBefore(cutoff: Instant)
}

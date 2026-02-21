package me.saramquantgateway.domain.repository.ai

import me.saramquantgateway.domain.entity.ai.StockAiAnalysis
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate

interface StockAiAnalysisRepository : JpaRepository<StockAiAnalysis, Long> {

    fun findByStockIdAndDateAndPresetAndLang(stockId: Long, date: LocalDate, preset: String, lang: String): StockAiAnalysis?

    fun deleteByCreatedAtBefore(cutoff: Instant)
}

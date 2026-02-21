package me.saramquantgateway.domain.repository.llm

import me.saramquantgateway.domain.entity.llm.StockLlmAnalysis
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.time.LocalDate

interface StockLlmAnalysisRepository : JpaRepository<StockLlmAnalysis, Long> {

    fun findByStockIdAndDateAndPresetAndLang(stockId: Long, date: LocalDate, preset: String, lang: String): StockLlmAnalysis?

    fun deleteByCreatedAtBefore(cutoff: Instant)
}

package me.saramquantgateway.feature.ai.service

import me.saramquantgateway.domain.repository.ai.StockAiAnalysisRepository
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class AiCacheCleanupScheduler(private val repo: StockAiAnalysisRepository) {

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanup() {
        repo.deleteByCreatedAtBefore(Instant.now().minus(30, ChronoUnit.DAYS))
    }
}

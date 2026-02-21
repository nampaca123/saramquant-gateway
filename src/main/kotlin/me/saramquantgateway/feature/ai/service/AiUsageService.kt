package me.saramquantgateway.feature.ai.service

import me.saramquantgateway.domain.repository.ai.AiUsageLogRepository
import me.saramquantgateway.feature.ai.dto.AiUsageResponse
import me.saramquantgateway.infra.ai.config.AiProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class AiUsageService(
    private val repo: AiUsageLogRepository,
    private val props: AiProperties,
) {
    @Transactional
    fun checkAndIncrement(userId: UUID): Int {
        repo.incrementUsage(userId, LocalDate.now())
        val log = repo.findByUserIdAndUsageDate(userId, LocalDate.now())
        return log?.count ?: 1
    }

    fun remaining(userId: UUID): AiUsageResponse {
        val log = repo.findByUserIdAndUsageDate(userId, LocalDate.now())
        val used = log?.count ?: 0
        return AiUsageResponse(used, props.dailyLimit, LocalDate.now().toString())
    }

    fun isWithinLimit(userId: UUID): Boolean {
        val log = repo.findByUserIdAndUsageDate(userId, LocalDate.now())
        return (log?.count ?: 0) < props.dailyLimit
    }
}

package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.domain.repository.llm.LlmUsageLogRepository
import me.saramquantgateway.feature.llm.dto.LlmUsageResponse
import me.saramquantgateway.infra.llm.config.LlmProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.util.UUID

@Service
class LlmUsageService(
    private val repo: LlmUsageLogRepository,
    private val props: LlmProperties,
) {
    @Transactional
    fun checkAndIncrement(userId: UUID): Int {
        repo.incrementUsage(userId, LocalDate.now())
        val log = repo.findByUserIdAndUsageDate(userId, LocalDate.now())
        return log?.count ?: 1
    }

    fun remaining(userId: UUID): LlmUsageResponse {
        val log = repo.findByUserIdAndUsageDate(userId, LocalDate.now())
        val used = log?.count ?: 0
        return LlmUsageResponse(used, props.dailyLimit, LocalDate.now().toString())
    }

    fun isWithinLimit(userId: UUID): Boolean {
        val log = repo.findByUserIdAndUsageDate(userId, LocalDate.now())
        return (log?.count ?: 0) < props.dailyLimit
    }

    @Transactional
    fun checkAndIncrementBy(userId: UUID, amount: Int): Boolean {
        val log = repo.findByUserIdAndUsageDate(userId, LocalDate.now())
        if ((log?.count ?: 0) + amount > props.dailyLimit) return false
        repo.incrementUsageBy(userId, LocalDate.now(), amount)
        return true
    }

    @Transactional
    fun decrementBy(userId: UUID, amount: Int) {
        repo.decrementUsageBy(userId, LocalDate.now(), amount)
    }
}

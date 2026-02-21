package me.saramquantgateway.domain.repository.ai

import me.saramquantgateway.domain.entity.ai.AiUsageLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface AiUsageLogRepository : JpaRepository<AiUsageLog, Long> {

    fun findByUserIdAndUsageDate(userId: UUID, usageDate: LocalDate): AiUsageLog?

    @Modifying
    @Query(
        nativeQuery = true,
        value = "INSERT INTO ai_usage_logs (user_id, usage_date, count) VALUES (:userId, :usageDate, 1) ON CONFLICT (user_id, usage_date) DO UPDATE SET count = ai_usage_logs.count + 1"
    )
    fun incrementUsage(@Param("userId") userId: UUID, @Param("usageDate") usageDate: LocalDate)
}

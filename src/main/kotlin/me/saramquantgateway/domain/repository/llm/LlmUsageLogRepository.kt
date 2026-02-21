package me.saramquantgateway.domain.repository.llm

import me.saramquantgateway.domain.entity.llm.LlmUsageLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate
import java.util.UUID

interface LlmUsageLogRepository : JpaRepository<LlmUsageLog, Long> {

    fun findByUserIdAndUsageDate(userId: UUID, usageDate: LocalDate): LlmUsageLog?

    @Modifying
    @Query(
        nativeQuery = true,
        value = "INSERT INTO llm_usage_logs (user_id, usage_date, count) VALUES (:userId, :usageDate, 1) ON CONFLICT (user_id, usage_date) DO UPDATE SET count = llm_usage_logs.count + 1"
    )
    fun incrementUsage(@Param("userId") userId: UUID, @Param("usageDate") usageDate: LocalDate)
}

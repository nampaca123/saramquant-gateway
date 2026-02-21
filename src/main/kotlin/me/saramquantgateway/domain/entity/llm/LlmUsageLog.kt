package me.saramquantgateway.domain.entity.llm

import jakarta.persistence.*
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "llm_usage_logs")
class LlmUsageLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "usage_date", nullable = false)
    val usageDate: LocalDate,

    @Column(nullable = false)
    val count: Int = 1,
)

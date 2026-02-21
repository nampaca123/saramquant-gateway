package me.saramquantgateway.domain.entity.ai

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "stock_ai_analyses")
class StockAiAnalysis(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "stock_id", nullable = false)
    val stockId: Long,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false, length = 30)
    val preset: String,

    @Column(nullable = false, length = 2)
    val lang: String = "ko",

    @Column(nullable = false, columnDefinition = "TEXT")
    val analysis: String,

    @Column(nullable = false, length = 50)
    val model: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

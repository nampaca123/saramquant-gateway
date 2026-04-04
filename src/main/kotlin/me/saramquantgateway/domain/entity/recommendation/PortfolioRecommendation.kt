package me.saramquantgateway.domain.entity.recommendation

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "portfolio_recommendations")
class PortfolioRecommendation(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "market_group", nullable = false, length = 2)
    val marketGroup: String,

    @Column(name = "risk_tolerance", nullable = false, length = 10)
    val riskTolerance: String,

    @Column(nullable = false, length = 2)
    val lang: String = "ko",

    @Column(nullable = false, columnDefinition = "jsonb")
    val stocks: String,

    @Column(nullable = false, columnDefinition = "TEXT")
    val reasoning: String,

    @Column(nullable = false, length = 50)
    val model: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)

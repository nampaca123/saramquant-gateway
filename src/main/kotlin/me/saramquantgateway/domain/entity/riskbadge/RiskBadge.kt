package me.saramquantgateway.domain.entity.riskbadge

import me.saramquantgateway.domain.enum.stock.Market
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "risk_badges")
class RiskBadge(
    @Id
    @Column(name = "stock_id")
    val stockId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "market_type")
    val market: Market,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(name = "summary_tier", nullable = false, length = 10)
    val summaryTier: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    val dimensions: Map<String, Any>,

    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
)

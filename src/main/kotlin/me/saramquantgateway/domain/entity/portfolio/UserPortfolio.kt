package me.saramquantgateway.domain.entity.portfolio

import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(
    name = "user_portfolios",
    uniqueConstraints = [UniqueConstraint(columnNames = ["user_id", "market_group"])]
)
class UserPortfolio(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "user_id", nullable = false)
    val userId: UUID,

    @Column(name = "market_group", nullable = false, length = 2)
    val marketGroup: String,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: Instant = Instant.now(),
)

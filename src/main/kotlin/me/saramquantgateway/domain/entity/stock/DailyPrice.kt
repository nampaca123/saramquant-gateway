package me.saramquantgateway.domain.entity.stock

import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "daily_prices")
class DailyPrice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "stock_id", nullable = false)
    val stockId: Long,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false, precision = 15, scale = 2)
    val open: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val high: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val low: BigDecimal,

    @Column(nullable = false, precision = 15, scale = 2)
    val close: BigDecimal,

    @Column(nullable = false)
    val volume: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
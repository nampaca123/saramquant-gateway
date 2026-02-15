package me.saramquantgateway.domain.entity.market

import me.saramquantgateway.domain.enum.market.Benchmark
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(name = "benchmark_daily_prices")
class BenchmarkDailyPrice(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "benchmark_type")
    val benchmark: Benchmark,

    @Column(nullable = false)
    val date: LocalDate,

    @Column(nullable = false, precision = 15, scale = 2)
    val close: BigDecimal,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
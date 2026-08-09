package me.saramquantgateway.domain.entity.market

import me.saramquantgateway.domain.enum.market.Benchmark
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class BenchmarkDailyPrice(
    val id: Long = 0,
    val benchmark: Benchmark,
    val date: LocalDate,
    val close: BigDecimal,
    val createdAt: Instant = Instant.now(),
)

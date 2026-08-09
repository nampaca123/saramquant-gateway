package me.saramquantgateway.domain.entity.stock

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class DailyPrice(
    val id: Long = 0,
    val stockId: Long,
    val date: LocalDate,
    val open: BigDecimal,
    val high: BigDecimal,
    val low: BigDecimal,
    val close: BigDecimal,
    val volume: Long,
    val createdAt: Instant = Instant.now(),
)

package me.saramquantgateway.domain.entity.fundamental

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class StockFundamental(
    val stockId: Long,
    val date: LocalDate,
    val per: BigDecimal? = null,
    val pbr: BigDecimal? = null,
    val eps: BigDecimal? = null,
    val bps: BigDecimal? = null,
    val roe: BigDecimal? = null,
    val debtRatio: BigDecimal? = null,
    val operatingMargin: BigDecimal? = null,
    val createdAt: Instant = Instant.now(),
)

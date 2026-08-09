package me.saramquantgateway.domain.entity.market

import java.math.BigDecimal
import java.time.LocalDate

data class ExchangeRate(
    val id: Long = 0,
    val pair: String,
    val date: LocalDate,
    val rate: BigDecimal,
)

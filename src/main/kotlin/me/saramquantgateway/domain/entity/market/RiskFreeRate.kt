package me.saramquantgateway.domain.entity.market

import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

data class RiskFreeRate(
    val id: Long = 0,
    val country: Country,
    val maturity: Maturity,
    val date: LocalDate,
    val rate: BigDecimal,
    val createdAt: Instant = Instant.now(),
)

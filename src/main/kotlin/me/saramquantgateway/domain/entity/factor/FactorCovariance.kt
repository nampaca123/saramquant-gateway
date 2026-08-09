package me.saramquantgateway.domain.entity.factor

import me.saramquantgateway.domain.enum.stock.Market
import java.time.LocalDate

data class FactorCovariance(
    val market: Market,
    val date: LocalDate,
    val matrix: String,
)

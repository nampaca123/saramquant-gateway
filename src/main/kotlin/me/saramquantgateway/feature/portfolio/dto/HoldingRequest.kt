package me.saramquantgateway.feature.portfolio.dto

import java.math.BigDecimal
import java.time.LocalDate

data class BuyRequest(
    val stockId: Long,
    val purchasedAt: LocalDate,
    val shares: BigDecimal,
    val manualPrice: BigDecimal? = null,
)

data class SellRequest(
    val sellShares: BigDecimal,
)

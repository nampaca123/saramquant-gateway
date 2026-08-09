package me.saramquantgateway.domain.entity.market

import me.saramquantgateway.domain.enum.stock.Market
import java.math.BigDecimal
import java.time.LocalDate

data class SectorAggregate(
    val market: Market,
    val sector: String,
    val date: LocalDate,
    val stockCount: Int,
    val medianPer: BigDecimal? = null,
    val medianPbr: BigDecimal? = null,
    val medianRoe: BigDecimal? = null,
    val medianOperatingMargin: BigDecimal? = null,
    val medianDebtRatio: BigDecimal? = null,
)

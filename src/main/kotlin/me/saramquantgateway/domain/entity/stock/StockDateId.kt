package me.saramquantgateway.domain.entity.stock

import java.io.Serializable
import java.time.LocalDate

data class StockDateId(
    val stockId: Long = 0,
    val date: LocalDate = LocalDate.MIN,
) : Serializable
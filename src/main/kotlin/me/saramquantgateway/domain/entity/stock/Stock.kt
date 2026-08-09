package me.saramquantgateway.domain.entity.stock

import me.saramquantgateway.domain.enum.stock.Market
import java.time.Instant

data class Stock(
    val id: Long = 0,
    val symbol: String,
    val name: String,
    val market: Market,
    val isActive: Boolean = true,
    val sector: String? = null,
    val dartCorpCode: String? = null,
    val createdAt: Instant = Instant.now(),
    val updatedAt: Instant = Instant.now(),
)

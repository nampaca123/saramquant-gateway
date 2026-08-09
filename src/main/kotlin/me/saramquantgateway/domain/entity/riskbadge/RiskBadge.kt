package me.saramquantgateway.domain.entity.riskbadge

import me.saramquantgateway.domain.enum.stock.Market
import java.time.Instant
import java.time.LocalDate

data class RiskBadge(
    val stockId: Long,
    val market: Market,
    val date: LocalDate,
    val summaryTier: String,
    val dimensions: Map<String, Any>,
    val updatedAt: Instant = Instant.now(),
)

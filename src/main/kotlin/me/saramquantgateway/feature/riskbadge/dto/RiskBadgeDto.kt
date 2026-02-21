package me.saramquantgateway.feature.riskbadge.dto

data class RiskBadgeItem(
    val stockId: Long,
    val symbol: String,
    val name: String,
    val market: String,
    val sector: String?,
    val summaryTier: String,
    val date: String,
    val dimensions: Map<String, Any>,
)

data class RiskBadgePage(
    val items: List<RiskBadgeItem>,
    val totalCount: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
    val hasNext: Boolean,
)

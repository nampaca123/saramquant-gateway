package me.saramquantgateway.domain.entity.fundamental

import me.saramquantgateway.domain.enum.fundamental.ReportType
import java.math.BigDecimal
import java.time.Instant

data class FinancialStatement(
    val id: Long = 0,
    val stockId: Long,
    val fiscalYear: Int,
    val reportType: ReportType,
    val revenue: BigDecimal? = null,
    val operatingIncome: BigDecimal? = null,
    val netIncome: BigDecimal? = null,
    val totalAssets: BigDecimal? = null,
    val totalLiabilities: BigDecimal? = null,
    val totalEquity: BigDecimal? = null,
    val sharesOutstanding: Long? = null,
    val createdAt: Instant = Instant.now(),
)

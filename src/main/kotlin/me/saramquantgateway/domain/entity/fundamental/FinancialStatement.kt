package me.saramquantgateway.domain.entity.fundamental

import me.saramquantgateway.domain.enum.fundamental.ReportType
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(name = "financial_statements")
class FinancialStatement(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "stock_id", nullable = false)
    val stockId: Long,

    @Column(name = "fiscal_year", nullable = false)
    val fiscalYear: Int,

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, columnDefinition = "report_type")
    val reportType: ReportType,

    val revenue: BigDecimal? = null,
    @Column(name = "operating_income")  val operatingIncome: BigDecimal? = null,
    @Column(name = "net_income")        val netIncome: BigDecimal? = null,
    @Column(name = "total_assets")      val totalAssets: BigDecimal? = null,
    @Column(name = "total_liabilities") val totalLiabilities: BigDecimal? = null,
    @Column(name = "total_equity")      val totalEquity: BigDecimal? = null,
    @Column(name = "shares_outstanding") val sharesOutstanding: Long? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
)
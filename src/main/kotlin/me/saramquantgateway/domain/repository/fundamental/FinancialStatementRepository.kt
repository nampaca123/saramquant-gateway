package me.saramquantgateway.domain.repository.fundamental

import me.saramquantgateway.domain.entity.fundamental.FinancialStatement
import org.springframework.data.jpa.repository.JpaRepository

interface FinancialStatementRepository : JpaRepository<FinancialStatement, Long> {

    fun findByStockIdOrderByFiscalYearDescReportTypeDesc(
        stockId: Long
    ): List<FinancialStatement>
}
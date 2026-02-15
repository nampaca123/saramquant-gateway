package me.saramquantgateway.domain.repository

import me.saramquantgateway.domain.entity.FinancialStatement
import org.springframework.data.jpa.repository.JpaRepository

interface FinancialStatementRepository : JpaRepository<FinancialStatement, Long> {

    fun findByStockIdOrderByFiscalYearDescReportTypeDesc(
        stockId: Long
    ): List<FinancialStatement>
}
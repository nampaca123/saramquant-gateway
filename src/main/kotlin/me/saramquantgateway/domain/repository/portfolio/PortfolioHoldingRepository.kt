package me.saramquantgateway.domain.repository.portfolio

import me.saramquantgateway.domain.entity.portfolio.PortfolioHolding
import org.springframework.data.jpa.repository.JpaRepository

interface PortfolioHoldingRepository : JpaRepository<PortfolioHolding, Long> {

    fun findByPortfolioId(portfolioId: Long): List<PortfolioHolding>

    fun findByPortfolioIdAndStockId(portfolioId: Long, stockId: Long): PortfolioHolding?

    fun deleteByPortfolioId(portfolioId: Long)
}

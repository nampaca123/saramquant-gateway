package me.saramquantgateway.domain.repository.stock

import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.stock.Market
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockRepository : JpaRepository<Stock, Long> {

    fun findBySymbolAndMarketAndIsActiveTrue(symbol: String, market: Market): Stock?

    fun findBySymbolAndIsActiveTrue(symbol: String): Stock?

    fun findByMarketAndIsActiveTrue(market: Market): List<Stock>

    fun findByIdIn(ids: List<Long>): List<Stock>

    fun findByMarketAndIsActiveTrue(market: Market, pageable: Pageable): Page<Stock>

    fun findByMarketAndSectorAndIsActiveTrue(market: Market, sector: String, pageable: Pageable): Page<Stock>

    @Query("SELECT DISTINCT s.sector FROM Stock s WHERE s.market = :market AND s.isActive = true AND s.sector IS NOT NULL ORDER BY s.sector")
    fun findDistinctSectorsByMarket(@Param("market") market: Market): List<String>
}
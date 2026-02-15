package me.saramquantgateway.domain.repository.stock

import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.stock.Market
import org.springframework.data.jpa.repository.JpaRepository

interface StockRepository : JpaRepository<Stock, Long> {

    fun findBySymbolAndMarketAndIsActiveTrue(symbol: String, market: Market): Stock?

    fun findBySymbolAndIsActiveTrue(symbol: String): Stock?

    fun findByMarketAndIsActiveTrue(market: Market): List<Stock>

    fun findByIdIn(ids: List<Long>): List<Stock>
}
package me.saramquantgateway.domain.repository

import me.saramquantgateway.domain.entity.Stock
import me.saramquantgateway.domain.enum.Market
import org.springframework.data.jpa.repository.JpaRepository

interface StockRepository : JpaRepository<Stock, Long> {

    fun findBySymbolAndMarketAndIsActiveTrue(symbol: String, market: Market): Stock?

    fun findBySymbolAndIsActiveTrue(symbol: String): Stock?

    fun findByMarketAndIsActiveTrue(market: Market): List<Stock>

    fun findByIdIn(ids: List<Long>): List<Stock>
}
package me.saramquantgateway.domain.repository

import me.saramquantgateway.domain.entity.StockDateId
import me.saramquantgateway.domain.entity.StockFundamental
import org.springframework.data.jpa.repository.JpaRepository

interface StockFundamentalRepository : JpaRepository<StockFundamental, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): StockFundamental?
}
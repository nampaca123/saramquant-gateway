package me.saramquantgateway.domain.repository.fundamental

import me.saramquantgateway.domain.entity.stock.StockDateId
import me.saramquantgateway.domain.entity.fundamental.StockFundamental
import org.springframework.data.jpa.repository.JpaRepository

interface StockFundamentalRepository : JpaRepository<StockFundamental, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): StockFundamental?
}
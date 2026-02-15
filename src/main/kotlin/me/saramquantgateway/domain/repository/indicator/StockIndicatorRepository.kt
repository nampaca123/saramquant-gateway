package me.saramquantgateway.domain.repository.indicator

import me.saramquantgateway.domain.entity.stock.StockDateId
import me.saramquantgateway.domain.entity.indicator.StockIndicator
import org.springframework.data.jpa.repository.JpaRepository

interface StockIndicatorRepository : JpaRepository<StockIndicator, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): StockIndicator?
}
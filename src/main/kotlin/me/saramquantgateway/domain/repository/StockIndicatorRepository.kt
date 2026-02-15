package me.saramquantgateway.domain.repository

import me.saramquantgateway.domain.entity.StockDateId
import me.saramquantgateway.domain.entity.StockIndicator
import org.springframework.data.jpa.repository.JpaRepository

interface StockIndicatorRepository : JpaRepository<StockIndicator, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): StockIndicator?
}
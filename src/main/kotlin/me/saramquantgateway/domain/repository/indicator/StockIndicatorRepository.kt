package me.saramquantgateway.domain.repository.indicator

import me.saramquantgateway.domain.entity.stock.StockDateId
import me.saramquantgateway.domain.entity.indicator.StockIndicator
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockIndicatorRepository : JpaRepository<StockIndicator, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): StockIndicator?

    @Query("SELECT si FROM StockIndicator si WHERE si.stockId IN :stockIds AND si.date = (SELECT MAX(si2.date) FROM StockIndicator si2 WHERE si2.stockId = si.stockId)")
    fun findLatestByStockIds(@Param("stockIds") stockIds: List<Long>): List<StockIndicator>
}
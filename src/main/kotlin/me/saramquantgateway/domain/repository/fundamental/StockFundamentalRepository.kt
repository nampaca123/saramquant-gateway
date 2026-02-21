package me.saramquantgateway.domain.repository.fundamental

import me.saramquantgateway.domain.entity.stock.StockDateId
import me.saramquantgateway.domain.entity.fundamental.StockFundamental
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface StockFundamentalRepository : JpaRepository<StockFundamental, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): StockFundamental?

    @Query("SELECT sf FROM StockFundamental sf WHERE sf.stockId IN :stockIds AND sf.date = (SELECT MAX(sf2.date) FROM StockFundamental sf2 WHERE sf2.stockId = sf.stockId)")
    fun findLatestByStockIds(@Param("stockIds") stockIds: List<Long>): List<StockFundamental>
}
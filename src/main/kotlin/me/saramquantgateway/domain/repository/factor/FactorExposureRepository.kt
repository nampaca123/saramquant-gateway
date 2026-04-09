package me.saramquantgateway.domain.repository.factor

import me.saramquantgateway.domain.entity.stock.StockDateId
import me.saramquantgateway.domain.entity.factor.FactorExposure
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface FactorExposureRepository : JpaRepository<FactorExposure, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): FactorExposure?

    fun findFirstByStockIdInOrderByDateDesc(stockIds: List<Long>): List<FactorExposure>

    fun findByStockIdInAndDate(stockIds: List<Long>, date: java.time.LocalDate): List<FactorExposure>

    @Query("SELECT fe FROM FactorExposure fe WHERE fe.stockId IN :stockIds AND fe.date = (SELECT MAX(fe2.date) FROM FactorExposure fe2 WHERE fe2.stockId = fe.stockId)")
    fun findLatestByStockIds(@Param("stockIds") stockIds: List<Long>): List<FactorExposure>
}

package me.saramquantgateway.domain.repository.factor

import me.saramquantgateway.domain.entity.stock.StockDateId
import me.saramquantgateway.domain.entity.factor.FactorExposure
import org.springframework.data.jpa.repository.JpaRepository

interface FactorExposureRepository : JpaRepository<FactorExposure, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): FactorExposure?

    fun findFirstByStockIdInOrderByDateDesc(stockIds: List<Long>): List<FactorExposure>

    fun findByStockIdInAndDate(stockIds: List<Long>, date: java.time.LocalDate): List<FactorExposure>
}

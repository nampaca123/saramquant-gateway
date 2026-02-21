package me.saramquantgateway.domain.repository.factor

import me.saramquantgateway.domain.entity.stock.StockDateId
import me.saramquantgateway.domain.entity.factor.FactorExposure
import org.springframework.data.jpa.repository.JpaRepository

interface FactorExposureRepository : JpaRepository<FactorExposure, StockDateId> {

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): FactorExposure?
}

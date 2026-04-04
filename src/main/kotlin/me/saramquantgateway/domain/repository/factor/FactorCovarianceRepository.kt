package me.saramquantgateway.domain.repository.factor

import me.saramquantgateway.domain.entity.factor.FactorCovariance
import me.saramquantgateway.domain.entity.factor.FactorCovarianceId
import me.saramquantgateway.domain.enum.stock.Market
import org.springframework.data.jpa.repository.JpaRepository

interface FactorCovarianceRepository : JpaRepository<FactorCovariance, FactorCovarianceId> {

    fun findTop1ByMarketOrderByDateDesc(market: Market): FactorCovariance?
}

package me.saramquantgateway.domain.repository.market

import me.saramquantgateway.domain.entity.market.RiskFreeRate
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import org.springframework.data.jpa.repository.JpaRepository

interface RiskFreeRateRepository : JpaRepository<RiskFreeRate, Long> {

    fun findTop1ByCountryAndMaturityOrderByDateDesc(
        country: Country, maturity: Maturity
    ): RiskFreeRate?
}
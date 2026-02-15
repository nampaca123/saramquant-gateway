package me.saramquantgateway.domain.repository

import me.saramquantgateway.domain.entity.RiskFreeRate
import me.saramquantgateway.domain.enum.Country
import me.saramquantgateway.domain.enum.Maturity
import org.springframework.data.jpa.repository.JpaRepository

interface RiskFreeRateRepository : JpaRepository<RiskFreeRate, Long> {

    fun findTop1ByCountryAndMaturityOrderByDateDesc(
        country: Country, maturity: Maturity
    ): RiskFreeRate?
}
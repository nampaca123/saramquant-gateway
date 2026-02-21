package me.saramquantgateway.domain.repository.market

import me.saramquantgateway.domain.entity.market.ExchangeRate
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface ExchangeRateRepository : JpaRepository<ExchangeRate, Long> {

    fun findTopByPairAndDateLessThanEqualOrderByDateDesc(
        pair: String, date: LocalDate
    ): ExchangeRate?
}

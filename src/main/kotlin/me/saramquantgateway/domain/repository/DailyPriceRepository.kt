package me.saramquantgateway.domain.repository

import me.saramquantgateway.domain.entity.DailyPrice
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface DailyPriceRepository : JpaRepository<DailyPrice, Long> {

    fun findByStockIdAndDateBetweenOrderByDateDesc(
        stockId: Long, startDate: LocalDate, endDate: LocalDate
    ): List<DailyPrice>

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): DailyPrice?
}
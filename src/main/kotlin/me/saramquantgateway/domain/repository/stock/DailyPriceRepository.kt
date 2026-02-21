package me.saramquantgateway.domain.repository.stock

import me.saramquantgateway.domain.entity.stock.DailyPrice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface DailyPriceRepository : JpaRepository<DailyPrice, Long> {

    fun findByStockIdAndDateBetweenOrderByDateDesc(
        stockId: Long, startDate: LocalDate, endDate: LocalDate
    ): List<DailyPrice>

    fun findTop1ByStockIdOrderByDateDesc(stockId: Long): DailyPrice?

    @Query("SELECT dp FROM DailyPrice dp WHERE dp.stockId IN :stockIds AND dp.date = (SELECT MAX(dp2.date) FROM DailyPrice dp2 WHERE dp2.stockId = dp.stockId)")
    fun findLatestByStockIds(@Param("stockIds") stockIds: List<Long>): List<DailyPrice>

    fun findTop2ByStockIdOrderByDateDesc(stockId: Long): List<DailyPrice>

    @Query(
        value = """
            SELECT id, stock_id, date, open, high, low, close, volume, created_at
            FROM (
                SELECT *, ROW_NUMBER() OVER (PARTITION BY stock_id ORDER BY date DESC) AS rn
                FROM daily_prices WHERE stock_id IN (:stockIds)
            ) t WHERE t.rn <= 2
        """,
        nativeQuery = true,
    )
    fun findTop2PerStockByStockIdIn(@Param("stockIds") stockIds: List<Long>): List<DailyPrice>
}
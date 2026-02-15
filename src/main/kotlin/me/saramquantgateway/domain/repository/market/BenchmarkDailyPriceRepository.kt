package me.saramquantgateway.domain.repository.market

import me.saramquantgateway.domain.entity.market.BenchmarkDailyPrice
import me.saramquantgateway.domain.enum.market.Benchmark
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface BenchmarkDailyPriceRepository : JpaRepository<BenchmarkDailyPrice, Long> {

    fun findByBenchmarkAndDateBetweenOrderByDateDesc(
        benchmark: Benchmark, startDate: LocalDate, endDate: LocalDate
    ): List<BenchmarkDailyPrice>

    fun findTop1ByBenchmarkOrderByDateDesc(benchmark: Benchmark): BenchmarkDailyPrice?
}
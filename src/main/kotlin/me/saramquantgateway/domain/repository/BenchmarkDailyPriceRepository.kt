package me.saramquantgateway.domain.repository

import me.saramquantgateway.domain.entity.BenchmarkDailyPrice
import me.saramquantgateway.domain.enum.Benchmark
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface BenchmarkDailyPriceRepository : JpaRepository<BenchmarkDailyPrice, Long> {

    fun findByBenchmarkAndDateBetweenOrderByDateDesc(
        benchmark: Benchmark, startDate: LocalDate, endDate: LocalDate
    ): List<BenchmarkDailyPrice>

    fun findTop1ByBenchmarkOrderByDateDesc(benchmark: Benchmark): BenchmarkDailyPrice?
}
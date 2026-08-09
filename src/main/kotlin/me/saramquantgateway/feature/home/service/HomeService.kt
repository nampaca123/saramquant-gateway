package me.saramquantgateway.feature.home.service

import me.saramquantgateway.domain.enum.market.Benchmark
import me.saramquantgateway.domain.repository.market.BenchmarkDailyPriceRepository
import me.saramquantgateway.domain.repository.riskbadge.RiskBadgeRepository
import me.saramquantgateway.domain.store.PortfolioStore
import me.saramquantgateway.feature.home.dto.*
import me.saramquantgateway.feature.portfolio.dto.PortfolioSummary
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

@Service
class HomeService(
    private val benchmarkRepo: BenchmarkDailyPriceRepository,
    private val badgeRepo: RiskBadgeRepository,
    private val portfolioStore: PortfolioStore,
) {

    fun summary(userId: UUID?): HomeSummary {
        val benchmarks = Benchmark.entries.map { buildBenchmarkSummary(it) }
        val overview = buildMarketOverview()
        val portfolios = userId?.let { buildPortfolioSummaries(it) }
        return HomeSummary(benchmarks, overview, portfolios)
    }

    private fun buildBenchmarkSummary(benchmark: Benchmark): BenchmarkSummary {
        val prices = benchmarkRepo.findTop2ByBenchmarkOrderByDateDesc(benchmark)
        val latest = prices.firstOrNull()
        val previous = prices.getOrNull(1)

        val changePct = if (latest != null && previous != null && previous.close.signum() != 0)
            latest.close.subtract(previous.close)
                .multiply(BigDecimal(100))
                .divide(previous.close, 2, RoundingMode.HALF_UP)
                .toDouble()
        else 0.0

        return BenchmarkSummary(
            benchmark = benchmark.name,
            latestClose = latest?.close ?: BigDecimal.ZERO,
            previousClose = previous?.close ?: BigDecimal.ZERO,
            changePercent = changePct,
            date = latest?.date?.toString() ?: "",
        )
    }

    private fun buildMarketOverview(): MarketOverview {
        val rows = badgeRepo.countByMarketAndTier()
        val distribution = rows.map { row ->
            MarketTierCount(
                market = row[0].toString(),
                tier = row[1].toString(),
                count = (row[2] as Number).toInt(),
            )
        }
        return MarketOverview(
            tierDistribution = distribution,
            totalStocks = distribution.sumOf { it.count },
        )
    }

    private fun buildPortfolioSummaries(userId: UUID): List<PortfolioSummary> =
        portfolioStore.findByUserId(userId)?.portfolios.orEmpty().map { p ->
            PortfolioSummary(
                id = p.id,
                marketGroup = p.marketGroup,
                holdingsCount = p.holdings.size,
                createdAt = p.createdAt,
            )
        }
}

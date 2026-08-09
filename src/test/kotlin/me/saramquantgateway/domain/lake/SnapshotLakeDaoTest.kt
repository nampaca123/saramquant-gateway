package me.saramquantgateway.domain.lake

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.saramquantgateway.domain.enum.fundamental.ReportType
import me.saramquantgateway.domain.enum.market.Country
import me.saramquantgateway.domain.enum.market.Maturity
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.support.LakeFixture
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate

class SnapshotLakeDaoTest {

    @Test
    fun `indicator dao maps every column of the latest snapshot row`() {
        val indicator = indicatorDao.findLatestByStockId(1L)

        assertEquals(today, indicator?.date)
        assertEquals("55.5000", indicator?.rsi14?.toPlainString())
        assertEquals("1.2000", indicator?.beta?.toPlainString())
        assertEquals(12345L, indicator?.obv)
        assertNull(indicator?.sharpe)
    }

    @Test
    fun `indicator dao returns one row per requested stock`() {
        val indicators = indicatorDao.findLatestByStockIds(listOf(1L, 2L, 99L))

        assertEquals(listOf(1L, 2L), indicators.map { it.stockId }.sorted())
        assertTrue(indicatorDao.findLatestByStockIds(emptyList()).isEmpty())
    }

    @Test
    fun `fundamental dao picks the newest row within the lookback window`() {
        val fundamental = fundamentalDao.findLatestByStockId(1L)

        assertEquals(today, fundamental?.date)
        assertEquals("10.0000", fundamental?.per?.toPlainString())
    }

    @Test
    fun `fundamental dao returns exactly one latest row per stock`() {
        val fundamentals = fundamentalDao.findLatestByStockIds(listOf(1L, 2L))

        assertEquals(listOf(1L, 2L), fundamentals.map { it.stockId }.sorted())
        assertEquals(today, fundamentals.single { it.stockId == 1L }.date)
    }

    @Test
    fun `financial statements keep fiscal year desc then postgres report type order`() {
        val statements = fundamentalDao.findFinancialsByStockId(1L, Market.KR_KOSPI)

        assertEquals(
            listOf(2025 to ReportType.Q1, 2024 to ReportType.FY, 2024 to ReportType.Q3),
            statements.map { it.fiscalYear to it.reportType },
        )
        assertEquals(1000L, statements.first { it.reportType == ReportType.FY }.sharesOutstanding)
    }

    @Test
    fun `financial statements are filtered by the market partition`() {
        assertTrue(fundamentalDao.findFinancialsByStockId(1L, Market.US_NYSE).isEmpty())
    }

    @Test
    fun `factor dao returns the latest exposure per stock`() {
        assertEquals("0.5000", factorDao.findLatestByStockId(1L)?.sizeZ?.toPlainString())
        assertEquals(today, factorDao.findLatestByStockIds(listOf(1L)).single().date)
    }

    @Test
    fun `factor covariance returns the matrix json unchanged`() {
        val covariance = factorDao.findLatestCovariance(Market.KR_KOSPI)

        assertEquals("[[1.0,0.0],[0.0,1.0]]", covariance?.matrix)
        assertEquals(today, covariance?.date)
        assertNull(factorDao.findLatestCovariance(Market.US_NYSE))
    }

    @Test
    fun `risk badge dimensions json is parsed into the nested map`() {
        val badge = badgeDao.findByStockId(1L)

        @Suppress("UNCHECKED_CAST")
        val dims = badge?.dimensions?.get("dims") as List<Map<String, Any>>
        assertEquals("A", badge.summaryTier)
        assertEquals(listOf("price_heat", "volatility"), dims.map { it["name"] })
        assertEquals(listOf("A", "C"), dims.map { it["tier"] })
        assertEquals(70, (dims.first()["score"] as Number).toInt())
    }

    @Test
    fun `risk badge page filters by market and tier and reports totals`() {
        val page = badgeDao.findPageByMarketAndTiers(Market.KR_KOSPI, listOf("A", "B"), 0, 1)

        assertEquals(2L, page.totalElements)
        assertEquals(listOf(1L), page.content.map { it.stockId })
        assertTrue(page.hasNext)
        assertTrue(badgeDao.findPageByMarketAndTiers(Market.US_NYSE, listOf("A"), 0, 10).content.isEmpty())
    }

    @Test
    fun `risk badge counts are grouped by market and tier`() {
        val counts = badgeDao.countByMarketAndTier().associateBy { it.market to it.summaryTier }

        assertEquals(1L, counts[Market.KR_KOSPI.name to "A"]?.count)
        assertEquals(1L, counts[Market.KR_KOSPI.name to "B"]?.count)
        assertEquals(1L, counts[Market.US_NASDAQ.name to "C"]?.count)
    }

    @Test
    fun `sector dao returns latest aggregates and same date rows`() {
        val latest = sectorDao.findLatestByMarket(Market.KR_KOSPI)

        assertEquals(today, latest?.date)
        assertEquals("IT", sectorDao.findLatestByMarketAndSector(Market.KR_KOSPI, "IT")?.sector)
        assertEquals(
            listOf("IT", "Platform"),
            sectorDao.findByMarketAndDate(Market.KR_KOSPI, today).map { it.sector },
        )
    }

    @Test
    fun `exchange rate falls back to the newest date on or before the requested one`() {
        val rate = marketRefDao.findExchangeRateOnOrBefore("USD/KRW", today)

        assertEquals(today.minusDays(2), rate?.date)
        assertEquals("1350.0000", rate?.rate?.toPlainString())
        assertNull(marketRefDao.findExchangeRateOnOrBefore("USD/KRW", today.minusDays(10)))
    }

    @Test
    fun `risk free rate is looked up by the maturity label`() {
        val rate = marketRefDao.findLatestRiskFreeRate(Country.KR, Maturity.Y1)

        assertEquals(Maturity.Y1, rate?.maturity)
        assertEquals(today, rate?.date)
        assertEquals("0.0325", rate?.rate?.toPlainString())
        assertNull(marketRefDao.findLatestRiskFreeRate(Country.US, Maturity.D91))
    }

    companion object {
        private val today: LocalDate = LocalDate.now()

        private lateinit var fixture: LakeFixture
        private lateinit var indicatorDao: IndicatorLakeDao
        private lateinit var fundamentalDao: FundamentalLakeDao
        private lateinit var factorDao: FactorLakeDao
        private lateinit var badgeDao: RiskBadgeLakeDao
        private lateinit var sectorDao: SectorLakeDao
        private lateinit var marketRefDao: MarketRefLakeDao

        @BeforeAll
        @JvmStatic
        fun setUp() {
            fixture = LakeFixture()
            fixture.execute(
                """INSERT INTO stock_indicators (stock_id, date, rsi_14, beta, obv, sharpe) VALUES
                    (1, DATE '$today', 55.5, 1.2, 12345, NULL),
                    (2, DATE '$today', 40.0, 0.8, 500, 1.1)""",
                """INSERT INTO stock_fundamentals (stock_id, date, per, pbr, roe, debt_ratio) VALUES
                    (1, DATE '$today', 10.0, 1.1, 0.15, 0.4),
                    (1, DATE '${today.minusDays(30)}', 9.0, 1.0, 0.14, 0.4),
                    (2, DATE '$today', 20.0, 2.2, 0.05, 0.9)""",
                """INSERT INTO financial_statements
                    (market, stock_id, fiscal_year, report_type, revenue, shares_outstanding) VALUES
                    ('KR', 1, 2024, 'FY', 500, 1000),
                    ('KR', 1, 2024, 'Q3', 300, 1000),
                    ('KR', 1, 2025, 'Q1', 100, 1000)""",
                """INSERT INTO factor_exposures (stock_id, date, size_z, value_z) VALUES
                    (1, DATE '$today', 0.5, -0.2),
                    (1, DATE '${today.minusDays(30)}', 0.1, -0.1)""",
                """INSERT INTO factor_covariance (market, date, matrix) VALUES
                    ('KR_KOSPI', DATE '$today', '[[1.0,0.0],[0.0,1.0]]'),
                    ('KR_KOSPI', DATE '${today.minusDays(1)}', '[[2.0,0.0],[0.0,2.0]]')""",
                """INSERT INTO risk_badges (stock_id, market, date, summary_tier, dimensions) VALUES
                    (1, 'KR_KOSPI', DATE '$today', 'A',
                     '{"dims":[{"name":"price_heat","tier":"A","score":70},{"name":"volatility","tier":"C","score":30}]}'),
                    (2, 'KR_KOSPI', DATE '$today', 'B', '{"dims":[{"name":"price_heat","tier":"B","score":50}]}'),
                    (4, 'US_NASDAQ', DATE '$today', 'C', '{"dims":[]}')""",
                """INSERT INTO sector_aggregates (market, sector, date, stock_count, median_per, median_roe) VALUES
                    ('KR_KOSPI', 'IT', DATE '$today', 10, 12.0, 0.12),
                    ('KR_KOSPI', 'Platform', DATE '$today', 5, 25.0, 0.08),
                    ('KR_KOSPI', 'IT', DATE '${today.minusDays(1)}', 10, 11.0, 0.11)""",
                """INSERT INTO exchange_rates (pair, date, rate) VALUES
                    ('USD/KRW', DATE '${today.minusDays(2)}', 1350.0),
                    ('USD/KRW', DATE '${today.minusDays(5)}', 1340.0)""",
                """INSERT INTO risk_free_rates (country, maturity, date, rate) VALUES
                    ('KR', '1Y', DATE '$today', 0.0325),
                    ('KR', '1Y', DATE '${today.minusDays(1)}', 0.0320),
                    ('KR', '91D', DATE '$today', 0.0300)""",
            )
            val objectMapper = jacksonObjectMapper()
            indicatorDao = IndicatorLakeDao(fixture.executor, fixture.resolver)
            fundamentalDao = FundamentalLakeDao(fixture.executor, fixture.resolver)
            factorDao = FactorLakeDao(fixture.executor, fixture.resolver)
            badgeDao = RiskBadgeLakeDao(fixture.executor, fixture.resolver, objectMapper)
            sectorDao = SectorLakeDao(fixture.executor, fixture.resolver)
            marketRefDao = MarketRefLakeDao(fixture.executor, fixture.resolver)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() = fixture.close()
    }
}

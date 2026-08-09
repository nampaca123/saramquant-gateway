package me.saramquantgateway.feature.dashboard.repository

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import me.saramquantgateway.infra.storage.s3.RunSummaryReader
import me.saramquantgateway.infra.storage.s3.S3KvStore
import me.saramquantgateway.infra.storage.s3.S3StorageProperties
import me.saramquantgateway.support.LakeFixture
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.math.BigDecimal
import java.time.LocalDate

class DashboardLakeDaoTest {

    @Test
    fun `search joins snapshots and the latest two prices`() {
        val page = dao.search(ScreenerFilter(market = "KR_KOSPI"))

        assertEquals(2L, page.totalElements)
        val samsung = page.content.single { it.symbol == "005930" }
        assertEquals("104.00", samsung.latestClose?.toPlainString())
        assertEquals(0.97, samsung.priceChangePercent)
        assertEquals(today.minusDays(1).toString(), samsung.comparedDate)
        assertEquals("55.5000", samsung.rsi14?.toPlainString())
        assertEquals("10.0000", samsung.per?.toPlainString())
        assertEquals("A", samsung.summaryTier)
    }

    @Test
    fun `search parses dimension tiers from the dimensions json`() {
        val samsung = dao.search(ScreenerFilter(market = "KR_KOSPI")).content.single { it.symbol == "005930" }

        assertEquals(mapOf("price_heat" to "A", "volatility" to "C"), samsung.dimensionTiers)
    }

    @Test
    fun `search filters by market and summary tier`() {
        val page = dao.search(ScreenerFilter(markets = listOf("KR_KOSPI"), tiers = listOf("B")))

        assertEquals(listOf("035720"), page.content.map { it.symbol })
    }

    @Test
    fun `search filters by a dimension tier inside the dimensions json`() {
        val heatA = dao.search(ScreenerFilter(market = "KR_KOSPI", priceHeatTiers = listOf("A")))
        val volatilityC = dao.search(ScreenerFilter(market = "KR_KOSPI", volatilityTiers = listOf("C")))
        val heatD = dao.search(ScreenerFilter(market = "KR_KOSPI", priceHeatTiers = listOf("D")))

        assertEquals(listOf("005930"), heatA.content.map { it.symbol })
        assertEquals(listOf("005930"), volatilityC.content.map { it.symbol })
        assertTrue(heatD.content.isEmpty())
    }

    @Test
    fun `search filters by an indicator range`() {
        val page = dao.search(ScreenerFilter(market = "KR_KOSPI", rsiMin = BigDecimal("50")))

        assertEquals(listOf("005930"), page.content.map { it.symbol })
    }

    @Test
    fun `search matches name and symbol case insensitively`() {
        assertEquals(listOf("035720"), dao.search(ScreenerFilter(query = "kak")).content.map { it.symbol })
        assertEquals(listOf("005930"), dao.search(ScreenerFilter(query = "0059")).content.map { it.symbol })
    }

    @Test
    fun `search paginates and sorts by the requested column`() {
        val first = dao.search(ScreenerFilter(sort = "name_desc", page = 0, size = 1))
        val second = dao.search(ScreenerFilter(sort = "name_desc", page = 1, size = 1))

        assertEquals(3L, first.totalElements)
        assertEquals(3, first.totalPages)
        assertTrue(first.hasNext)
        assertEquals(listOf("Samsung"), first.content.map { it.name })
        assertEquals(listOf("Kakao"), second.content.map { it.name })
    }

    @Test
    fun `search excludes stock ids and returns an empty page when nothing matches`() {
        val page = dao.search(ScreenerFilter(market = "KR_KOSPI", excludeStockIds = listOf(1L, 3L)))

        assertEquals(0L, page.totalElements)
        assertEquals(0, page.totalPages)
        assertTrue(page.content.isEmpty())
    }

    @Test
    fun `search sorts nulls last`() {
        val page = dao.search(ScreenerFilter(sort = "per_asc"))

        assertEquals(listOf("005930", "035720", "AAPL"), page.content.map { it.symbol })
    }

    @Test
    fun `searchStocks prefers symbol prefix matches and honours the limit`() {
        val results = dao.searchStocks("ka", null, 10)
        val single = dao.searchStocks("a", null, 10)

        assertEquals(listOf("035720"), results.map { it.symbol })
        assertEquals(listOf("AAPL"), single.map { it.symbol })
        assertEquals(1, dao.searchStocks("a", Market.US_NASDAQ, 1).size)
        assertTrue(dao.searchStocks("a", Market.KR_KOSPI, 10).isEmpty())
    }

    @Test
    fun `dataFreshness returns null fields when no run summary exists`() {
        val freshness = dao.dataFreshness()

        assertNull(freshness.krPriceUpdatedAt)
        assertNull(freshness.usFinancialUpdatedAt)
    }

    companion object {
        private val today: LocalDate = LocalDate.now()

        private lateinit var fixture: LakeFixture
        private lateinit var dao: DashboardLakeDao

        @BeforeAll
        @JvmStatic
        fun setUp() {
            fixture = LakeFixture()
            fixture.execute(
                """INSERT INTO stocks (id, symbol, name, market, is_active, sector) VALUES
                    (1, '005930', 'Samsung', 'KR_KOSPI', true, 'IT'),
                    (3, '035720', 'Kakao', 'KR_KOSPI', true, 'Platform'),
                    (4, 'AAPL', 'Apple', 'US_NASDAQ', true, 'Tech'),
                    (5, 'GONE', 'Delisted', 'KR_KOSPI', false, 'IT')""",
                """INSERT INTO daily_prices (market, stock_id, date, open, high, low, close, volume) VALUES
                    ('KR', 1, DATE '$today', 100, 105, 99, 104, 1500),
                    ('KR', 1, DATE '${today.minusDays(1)}', 100, 104, 98, 103, 1400),
                    ('KR', 3, DATE '$today', 50, 52, 49, 51, 900),
                    ('US', 4, DATE '$today', 200, 205, 199, 204, 5000)""",
                """INSERT INTO stock_indicators (stock_id, date, rsi_14, beta, sharpe) VALUES
                    (1, DATE '$today', 55.5, 1.2, 0.9),
                    (3, DATE '$today', 45.0, 0.9, 0.4)""",
                """INSERT INTO stock_fundamentals (stock_id, date, per, pbr, roe, debt_ratio) VALUES
                    (1, DATE '$today', 10.0, 1.1, 0.15, 0.4),
                    (3, DATE '$today', 20.0, 2.2, 0.05, 0.9)""",
                """INSERT INTO risk_badges (stock_id, market, date, summary_tier, dimensions) VALUES
                    (1, 'KR_KOSPI', DATE '$today', 'A',
                     '{"dims":[{"name":"price_heat","tier":"A","score":70},{"name":"volatility","tier":"C","score":30}]}'),
                    (3, 'KR_KOSPI', DATE '$today', 'B', '{"dims":[{"name":"price_heat","tier":"B","score":50}]}'),
                    (4, 'US_NASDAQ', DATE '$today', 'C', '{"dims":[]}')""",
            )
            dao = DashboardLakeDao(fixture.executor, fixture.resolver, offlineRunSummaryReader(), jacksonObjectMapper())
        }

        // 로컬 테스트는 S3를 건드리지 않는다 — 존재하지 않는 버킷이라 항상 null 신선도를 돌려준다.
        private fun offlineRunSummaryReader(): RunSummaryReader {
            val s3 = S3Client.builder()
                .region(Region.of("ap-northeast-2"))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .build()
            return object : RunSummaryReader(
                S3KvStore(s3, S3StorageProperties("saramquant-bucket", "app/", "ap-northeast-2"), jacksonObjectMapper()),
                "run-summary/",
            ) {
                override fun writtenAtUtc(command: String): String? = null
            }
        }

        @AfterAll
        @JvmStatic
        fun tearDown() = fixture.close()
    }
}

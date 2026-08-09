package me.saramquantgateway.domain.lake

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import me.saramquantgateway.domain.enum.portfolio.MarketGroup
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.feature.dashboard.dto.ScreenerFilter
import me.saramquantgateway.feature.dashboard.repository.DashboardLakeDao
import me.saramquantgateway.infra.storage.s3.RunSummaryReader
import me.saramquantgateway.infra.storage.s3.S3KvStore
import me.saramquantgateway.infra.storage.s3.S3StorageProperties
import me.saramquantgateway.support.LakeFixture
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import java.time.LocalDate

// 마이그레이션 회귀(중복 행 팬아웃, 조회 기간, 정렬 화이트리스트, 신선도 포맷) 검증.
class LakeRegressionTest {

    @Test
    fun `duplicate stock_indicators rows do not fan out the screener page`() {
        val page = dashboardDao.search(ScreenerFilter(market = "KR_KOSPI"))

        assertEquals(2L, page.totalElements)
        assertEquals(1, page.content.count { it.symbol == "005930" })
        assertEquals("60.0000", page.content.single { it.symbol == "005930" }.rsi14?.toPlainString())
    }

    @Test
    fun `findLatestByStockIds keeps only the newest indicator row per stock`() {
        val indicators = IndicatorLakeDao(fixture.executor, fixture.resolver).findLatestByStockIds(listOf(1L))

        assertEquals(1, indicators.size)
        assertEquals(today, indicators.single().date)
    }

    @Test
    fun `findTop2ByStockId still returns prices older than the multi-stock lookback window`() {
        val prices = priceDao.findTop2ByStockId(2L, Market.KR_KOSPI)

        assertEquals(listOf(today.minusDays(400)), prices.map { it.date })
    }

    @Test
    fun `findTop2PerStock anchors its window to the newest date in the table`() {
        val prices = priceDao.findTop2PerStock(listOf(1L, 2L), MarketGroup.KR)

        assertEquals(listOf(1L), prices.map { it.stockId }.distinct())
    }

    @Test
    fun `an unknown sort key falls back to the name ascending whitelist entry`() {
        val page = dashboardDao.search(ScreenerFilter(sort = "name; DROP TABLE stocks--"))

        assertEquals(listOf("Halted", "Samsung"), page.content.map { it.name })
    }

    @Test
    fun `dataFreshness keeps the pre-migration timestamp format`() {
        val freshness = DashboardLakeDao(
            fixture.executor, fixture.resolver, runSummaryReader("2026-08-09T12:00:00Z"), jacksonObjectMapper(),
        ).dataFreshness()

        assertEquals("2026-08-09 12:00:00+00", freshness.krPriceUpdatedAt)
        assertEquals("2026-08-09 12:00:00+00", freshness.usFinancialUpdatedAt)
    }

    companion object {
        private val today: LocalDate = LocalDate.now()

        private lateinit var fixture: LakeFixture
        private lateinit var dashboardDao: DashboardLakeDao
        private lateinit var priceDao: PriceLakeDao

        @BeforeAll
        @JvmStatic
        fun setUp() {
            fixture = LakeFixture()
            fixture.execute(
                """INSERT INTO stocks (id, symbol, name, market, is_active, sector) VALUES
                    (1, '005930', 'Samsung', 'KR_KOSPI', true, 'IT'),
                    (2, '000660', 'Halted', 'KR_KOSPI', true, 'IT')""",
                """INSERT INTO daily_prices (market, stock_id, date, open, high, low, close, volume) VALUES
                    ('KR', 1, DATE '$today', 100, 105, 99, 104, 1500),
                    ('KR', 2, DATE '${today.minusDays(400)}', 50, 52, 49, 51, 900)""",
                // DELETE 후 INSERT 재시도로 같은 종목에 두 행이 남은 상황을 재현한다.
                """INSERT INTO stock_indicators (stock_id, date, rsi_14) VALUES
                    (1, DATE '$today', 60.0),
                    (1, DATE '${today.minusDays(1)}', 40.0)""",
            )
            priceDao = PriceLakeDao(fixture.executor, fixture.resolver)
            dashboardDao = DashboardLakeDao(
                fixture.executor, fixture.resolver, runSummaryReader(null), jacksonObjectMapper(),
            )
        }

        private fun runSummaryReader(value: String?): RunSummaryReader {
            val s3 = S3Client.builder()
                .region(Region.of("ap-northeast-2"))
                .credentialsProvider(AnonymousCredentialsProvider.create())
                .build()
            return object : RunSummaryReader(
                S3KvStore(s3, S3StorageProperties("saramquant-bucket", "app/", "ap-northeast-2"), jacksonObjectMapper()),
                "run-summary/",
            ) {
                override fun writtenAtUtc(command: String): String? = value
            }
        }

        @AfterAll
        @JvmStatic
        fun tearDown() = fixture.close()
    }
}

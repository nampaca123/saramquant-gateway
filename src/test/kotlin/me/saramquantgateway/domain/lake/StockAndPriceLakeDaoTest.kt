package me.saramquantgateway.domain.lake

import me.saramquantgateway.domain.enum.market.Benchmark
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.support.LakeFixture
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.time.LocalDate

class StockAndPriceLakeDaoTest {

    @Test
    fun `findBySymbolAndMarket matches symbol and market and skips inactive rows`() {
        val stock = stockDao.findBySymbolAndMarket("005930", Market.KR_KOSPI)

        assertEquals(1L, stock?.id)
        assertEquals("Samsung", stock?.name)
        assertEquals(Market.KR_KOSPI, stock?.market)
        assertEquals("IT", stock?.sector)
    }

    @Test
    fun `findBySymbolAndMarket ignores the same symbol in another market`() {
        assertNull(stockDao.findBySymbolAndMarket("005930", Market.US_NYSE))
    }

    @Test
    fun `findBySymbolAndMarket ignores inactive stocks`() {
        assertNull(stockDao.findBySymbolAndMarket("DEAD", Market.KR_KOSPI))
    }

    @Test
    fun `findByIds returns only the requested stocks`() {
        val stocks = stockDao.findByIds(listOf(1L, 3L))

        assertEquals(listOf(1L, 3L), stocks.map { it.id }.sorted())
    }

    @Test
    fun `findByIds short circuits on an empty id list`() {
        assertTrue(stockDao.findByIds(emptyList()).isEmpty())
    }

    @Test
    fun `findPageByMarket paginates by name and reports hasNext`() {
        val first = stockDao.findPageByMarket(Market.KR_KOSPI, null, 0, 1)
        val second = stockDao.findPageByMarket(Market.KR_KOSPI, null, 1, 1)

        assertEquals(2L, first.totalElements)
        assertEquals(listOf("Kakao"), first.content.map { it.name })
        assertTrue(first.hasNext)
        assertEquals(listOf("Samsung"), second.content.map { it.name })
        assertTrue(!second.hasNext)
    }

    @Test
    fun `findPageByMarket filters by sector`() {
        val page = stockDao.findPageByMarket(Market.KR_KOSPI, "IT", 0, 10)

        assertEquals(1L, page.totalElements)
        assertEquals("Samsung", page.content.single().name)
    }

    @Test
    fun `findDistinctSectors returns sorted sectors per market and overall`() {
        assertEquals(listOf("IT", "Platform"), stockDao.findDistinctSectors(Market.KR_KOSPI))
        assertEquals(listOf("IT", "Platform", "Tech"), stockDao.findDistinctSectors(null))
    }

    @Test
    fun `findByStockIdAndDateBetween returns the range ordered by date desc`() {
        val prices = priceDao.findByStockIdAndDateBetween(1L, Market.KR_KOSPI, today.minusDays(3), today.minusDays(1))

        assertEquals(listOf(today.minusDays(1), today.minusDays(2), today.minusDays(3)), prices.map { it.date })
        assertEquals("103.00", prices.first().close.toPlainString())
    }

    @Test
    fun `findByStockIdAndDateBetween filters by the market partition`() {
        val prices = priceDao.findByStockIdAndDateBetween(1L, Market.US_NYSE, today.minusDays(10), today)

        assertTrue(prices.isEmpty())
    }

    @Test
    fun `findTop2ByStockId returns the two most recent rows`() {
        val prices = priceDao.findTop2ByStockId(1L, Market.KR_KOSPI)

        assertEquals(listOf(today, today.minusDays(1)), prices.map { it.date })
        assertEquals("104.00", prices.first().close.toPlainString())
        assertEquals(1500L, prices.first().volume)
    }

    @Test
    fun `findTop2PerStock returns at most two rows per stock and tolerates a single row stock`() {
        val prices = priceDao.findTop2PerStock(listOf(1L, 3L), "KR")

        assertEquals(2, prices.count { it.stockId == 1L })
        assertEquals(1, prices.count { it.stockId == 3L })
        assertEquals(listOf(today, today.minusDays(1)), prices.filter { it.stockId == 1L }.map { it.date })
    }

    @Test
    fun `findTop2PerStock excludes stocks from another market group`() {
        val prices = priceDao.findTop2PerStock(listOf(1L, 4L), "US")

        assertEquals(listOf(4L), prices.map { it.stockId }.distinct())
    }

    @Test
    fun `findBenchmarkByDateBetween returns the range ordered by date desc`() {
        val prices = priceDao.findBenchmarkByDateBetween(Benchmark.KR_KOSPI, today.minusDays(2), today)

        assertEquals(listOf(today, today.minusDays(1), today.minusDays(2)), prices.map { it.date })
        assertEquals(Benchmark.KR_KOSPI, prices.first().benchmark)
    }

    @Test
    fun `findTop2Benchmark returns two rows and only one when history is short`() {
        assertEquals(2, priceDao.findTop2Benchmark(Benchmark.KR_KOSPI).size)
        assertEquals(1, priceDao.findTop2Benchmark(Benchmark.US_SP500).size)
        assertTrue(priceDao.findTop2Benchmark(Benchmark.US_NASDAQ).isEmpty())
    }

    companion object {
        private val today: LocalDate = LocalDate.now()

        private lateinit var fixture: LakeFixture
        private lateinit var stockDao: StockLakeDao
        private lateinit var priceDao: PriceLakeDao

        @BeforeAll
        @JvmStatic
        fun setUp() {
            fixture = LakeFixture()
            fixture.execute(
                """INSERT INTO stocks (id, symbol, name, market, is_active, sector) VALUES
                    (1, '005930', 'Samsung', 'KR_KOSPI', true, 'IT'),
                    (2, 'DEAD', 'Delisted', 'KR_KOSPI', false, 'IT'),
                    (3, '035720', 'Kakao', 'KR_KOSPI', true, 'Platform'),
                    (4, 'AAPL', 'Apple', 'US_NASDAQ', true, 'Tech')""",
                """INSERT INTO daily_prices (market, stock_id, date, open, high, low, close, volume) VALUES
                    ('KR', 1, DATE '$today', 100, 105, 99, 104, 1500),
                    ('KR', 1, DATE '${today.minusDays(1)}', 100, 104, 98, 103, 1400),
                    ('KR', 1, DATE '${today.minusDays(2)}', 100, 103, 97, 102, 1300),
                    ('KR', 1, DATE '${today.minusDays(3)}', 100, 102, 96, 101, 1200),
                    ('KR', 3, DATE '$today', 50, 52, 49, 51, 900),
                    ('US', 4, DATE '$today', 200, 205, 199, 204, 5000)""",
                """INSERT INTO benchmark_daily_prices (benchmark, date, close) VALUES
                    ('KR_KOSPI', DATE '$today', 2600),
                    ('KR_KOSPI', DATE '${today.minusDays(1)}', 2580),
                    ('KR_KOSPI', DATE '${today.minusDays(2)}', 2570),
                    ('US_SP500', DATE '$today', 5500)""",
            )
            stockDao = StockLakeDao(fixture.executor, fixture.resolver)
            priceDao = PriceLakeDao(fixture.executor, fixture.resolver)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() = fixture.close()
    }
}

package me.saramquantgateway.feature.portfolio.service

import me.saramquantgateway.domain.document.HoldingEntry
import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.lake.StockLakeDao
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.time.LocalDate

class CalcPortfolioRequestBuilderTest {

    private lateinit var stockDao: StockLakeDao
    private lateinit var builder: CalcPortfolioRequestBuilder

    @BeforeEach
    fun setUp() {
        stockDao = Mockito.mock(StockLakeDao::class.java)
        builder = CalcPortfolioRequestBuilder(stockDao)
    }

    @Test
    fun `build maps holdings to the calc contract shape`() {
        val holdings = listOf(
            holding(7, fxRate = null),
            holding(8, currency = "USD", fxRate = BigDecimal("1300.5")),
        )
        Mockito.`when`(stockDao.findByIds(listOf(7L, 8L))).thenReturn(
            listOf(stock(7, Market.KR_KOSPI), stock(8, Market.US_NASDAQ)),
        )

        val body = builder.build("KR", holdings)

        assertEquals("KR", body["market_group"])
        @Suppress("UNCHECKED_CAST")
        val rows = body["holdings"] as List<Map<String, Any?>>
        assertEquals(2, rows.size)
        assertEquals(
            setOf("symbol", "market", "shares", "avg_price", "currency", "purchased_at", "purchase_fx_rate"),
            rows[0].keys,
        )
        assertEquals("TEST7", rows[0]["symbol"])
        assertEquals("KR_KOSPI", rows[0]["market"])
        assertEquals(BigDecimal("10"), rows[0]["shares"])
        assertEquals(BigDecimal("1000"), rows[0]["avg_price"])
        assertEquals("KRW", rows[0]["currency"])
        assertEquals("2026-01-02", rows[0]["purchased_at"])
        assertEquals(null, rows[0]["purchase_fx_rate"])
        assertEquals("US_NASDAQ", rows[1]["market"])
        assertEquals("USD", rows[1]["currency"])
        assertEquals(BigDecimal("1300.5"), rows[1]["purchase_fx_rate"])
    }

    @Test
    fun `build drops holdings whose stock cannot be resolved`() {
        Mockito.`when`(stockDao.findByIds(listOf(7L, 99L))).thenReturn(listOf(stock(7, Market.KR_KOSPI)))

        val body = builder.build("KR", listOf(holding(7), holding(99)))

        @Suppress("UNCHECKED_CAST")
        val rows = body["holdings"] as List<Map<String, Any?>>
        assertEquals(1, rows.size)
        assertEquals("TEST7", rows[0]["symbol"])
    }

    @Test
    fun `build returns an empty holdings array for an empty portfolio`() {
        Mockito.`when`(stockDao.findByIds(emptyList())).thenReturn(emptyList())

        val body = builder.build("US", emptyList())

        assertEquals("US", body["market_group"])
        assertTrue((body["holdings"] as List<*>).isEmpty())
    }

    private fun stock(stockId: Long, market: Market) =
        Stock(id = stockId, symbol = "TEST$stockId", name = "Test $stockId", market = market)

    private fun holding(
        stockId: Long,
        currency: String = "KRW",
        fxRate: BigDecimal? = null,
    ) = HoldingEntry(
        stockId = stockId,
        shares = BigDecimal("10"),
        avgPrice = BigDecimal("1000"),
        currency = currency,
        purchasedAt = LocalDate.of(2026, 1, 2),
        purchaseFxRate = fxRate,
    )
}

package me.saramquantgateway.feature.portfolio.service

import me.saramquantgateway.domain.document.HoldingEntry
import me.saramquantgateway.domain.document.PortfolioDoc
import me.saramquantgateway.domain.document.PortfolioEntry
import me.saramquantgateway.domain.entity.stock.Stock
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.lake.PriceLakeDao
import me.saramquantgateway.domain.lake.RiskBadgeLakeDao
import me.saramquantgateway.domain.lake.StockLakeDao
import me.saramquantgateway.domain.store.PortfolioStore
import me.saramquantgateway.feature.portfolio.dto.BuyRequest
import me.saramquantgateway.feature.portfolio.dto.SellRequest
import me.saramquantgateway.infra.connection.CalcServerClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class PortfolioServiceTest {

    private val userId: UUID = UUID.randomUUID()
    private val krId: Long = PortfolioStore.portfolioIdOf(userId, "KR")
    private val usId: Long = PortfolioStore.portfolioIdOf(userId, "US")

    private lateinit var store: PortfolioStore
    private lateinit var stockDao: StockLakeDao
    private lateinit var badgeDao: RiskBadgeLakeDao
    private lateinit var priceDao: PriceLakeDao
    private lateinit var calcClient: CalcServerClient
    private lateinit var service: PortfolioService

    @BeforeEach
    fun setUp() {
        store = Mockito.mock(PortfolioStore::class.java)
        stockDao = Mockito.mock(StockLakeDao::class.java)
        badgeDao = Mockito.mock(RiskBadgeLakeDao::class.java)
        priceDao = Mockito.mock(PriceLakeDao::class.java)
        calcClient = Mockito.mock(CalcServerClient::class.java)
        service = PortfolioService(store, stockDao, badgeDao, priceDao, calcClient)
    }

    @Test
    fun `ensurePortfoliosExist creates KR and US with deterministic ids`() {
        Mockito.`when`(store.findByUserId(userId)).thenReturn(null)

        val doc = service.ensurePortfoliosExist(userId)

        assertEquals(listOf(krId, usId), doc.portfolios.map { it.id })
        assertEquals(listOf("KR", "US"), doc.portfolios.map { it.marketGroup })
        Mockito.verify(store).save(doc)
    }

    @Test
    fun `ensurePortfoliosExist does not rewrite an already complete doc`() {
        val doc = stubDoc()

        service.ensurePortfoliosExist(userId)

        Mockito.verify(store, Mockito.never()).save(doc)
    }

    @Test
    fun `getPortfolios reports holdings count from the document`() {
        val doc = stubDoc(holding(stockId = 7))

        val summaries = service.getPortfolios(userId)

        assertEquals(2, summaries.size)
        assertEquals(1, summaries.first { it.id == krId }.holdingsCount)
        assertEquals(0, summaries.first { it.id == usId }.holdingsCount)
        Mockito.verify(store, Mockito.never()).save(doc)
    }

    @Test
    fun `buy adds a new holding at the manual price`() {
        val doc = stubDoc()
        stubStock(7, Market.KR_KOSPI)

        val result = service.buy(krId, userId, buyRequest(shares = "10", price = "1000"))

        assertEquals(7L, result.stockId)
        assertEquals(7L, result.id)
        assertEquals(0, BigDecimal("10").compareTo(result.shares))
        assertEquals(0, BigDecimal("1000").compareTo(result.avgPrice))
        assertEquals("KRW", result.currency)
        assertEquals("MANUAL", result.priceSource)
        assertEquals(1, krEntry(doc).holdings.size)
        Mockito.verify(store).save(doc)
    }

    @Test
    fun `buy on an existing holding recalculates the average price`() {
        val doc = stubDoc(holding(stockId = 7, shares = "10", avgPrice = "1000"))
        stubStock(7, Market.KR_KOSPI)

        val result = service.buy(krId, userId, buyRequest(shares = "10", price = "2000"))

        assertEquals(0, BigDecimal("20").compareTo(result.shares))
        assertEquals(0, BigDecimal("1500").compareTo(result.avgPrice))
        assertEquals(1, krEntry(doc).holdings.size)
        Mockito.verify(store).save(doc)
    }

    @Test
    fun `buy rejects a US stock in the KR portfolio`() {
        stubDoc()
        stubStock(7, Market.US_NASDAQ)

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.buy(krId, userId, buyRequest(shares = "10", price = "1000"))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `sell reduces shares on a partial sell`() {
        val doc = stubDoc(holding(stockId = 7, shares = "10", avgPrice = "1000"))

        service.sell(krId, 7, userId, SellRequest(BigDecimal("4")))

        assertEquals(0, BigDecimal("6").compareTo(krEntry(doc).holdings[0].shares))
        Mockito.verify(store).save(doc)
    }

    @Test
    fun `sell removes the holding on a full sell`() {
        val doc = stubDoc(holding(stockId = 7, shares = "10", avgPrice = "1000"))

        service.sell(krId, 7, userId, SellRequest(BigDecimal("10")))

        assertTrue(krEntry(doc).holdings.isEmpty())
        Mockito.verify(store).save(doc)
    }

    @Test
    fun `sell rejects more shares than owned`() {
        stubDoc(holding(stockId = 7, shares = "10", avgPrice = "1000"))

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sell(krId, 7, userId, SellRequest(BigDecimal("11")))
        }
        assertEquals(HttpStatus.BAD_REQUEST, ex.statusCode)
    }

    @Test
    fun `sell rejects an unknown holding`() {
        stubDoc()

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.sell(krId, 99, userId, SellRequest(BigDecimal("1")))
        }
        assertEquals(HttpStatus.NOT_FOUND, ex.statusCode)
    }

    @Test
    fun `reset clears only the target portfolio`() {
        val doc = stubDoc(holding(stockId = 7))
        doc.portfolios.first { it.id == usId }.holdings += holding(stockId = 8, currency = "USD")

        service.reset(krId, userId)

        assertTrue(krEntry(doc).holdings.isEmpty())
        assertEquals(1, doc.portfolios.first { it.id == usId }.holdings.size)
        Mockito.verify(store).save(doc)
    }

    @Test
    fun `deleteHolding removes the holding`() {
        val doc = stubDoc(holding(stockId = 7))

        service.deleteHolding(krId, 7, userId)

        assertTrue(krEntry(doc).holdings.isEmpty())
        Mockito.verify(store).save(doc)
    }

    @Test
    fun `verifyOwnership rejects a portfolio belonging to another user`() {
        stubDoc()
        val foreignId = PortfolioStore.portfolioIdOf(UUID.randomUUID(), "KR")

        val ex = assertThrows(ResponseStatusException::class.java) {
            service.verifyOwnership(foreignId, userId)
        }
        assertEquals(HttpStatus.FORBIDDEN, ex.statusCode)
    }

    @Test
    fun `getPortfolioDetail maps holdings without prices`() {
        stubDoc(holding(stockId = 7, shares = "10", avgPrice = "1000"))
        Mockito.`when`(stockDao.findByIds(listOf(7L))).thenReturn(listOf(stock(7, Market.KR_KOSPI)))

        val detail = service.getPortfolioDetail(krId, userId)

        assertEquals(krId, detail.id)
        assertEquals("KR", detail.marketGroup)
        assertEquals(1, detail.holdings.size)
        assertEquals("TEST7", detail.holdings[0].symbol)
        assertNull(detail.holdings[0].latestClose)
        assertEquals(0, BigDecimal("10000.00").compareTo(detail.totalCost!!))
    }

    private fun stubDoc(vararg holdings: HoldingEntry): PortfolioDoc {
        val doc = PortfolioDoc(
            userId = userId,
            portfolios = mutableListOf(
                PortfolioEntry(id = krId, marketGroup = "KR", holdings = holdings.toMutableList()),
                PortfolioEntry(id = usId, marketGroup = "US"),
            ),
        )
        Mockito.`when`(store.findByUserId(userId)).thenReturn(doc)
        return doc
    }

    private fun stubStock(stockId: Long, market: Market) {
        Mockito.`when`(stockDao.findById(stockId)).thenReturn(stock(stockId, market))
    }

    private fun stock(stockId: Long, market: Market) =
        Stock(id = stockId, symbol = "TEST$stockId", name = "Test $stockId", market = market)

    private fun krEntry(doc: PortfolioDoc) = doc.portfolios.first { it.id == krId }

    private fun buyRequest(shares: String, price: String) = BuyRequest(
        stockId = 7,
        purchasedAt = LocalDate.of(2026, 1, 2),
        shares = BigDecimal(shares),
        manualPrice = BigDecimal(price),
    )

    private fun holding(
        stockId: Long,
        shares: String = "10",
        avgPrice: String = "1000",
        currency: String = "KRW",
    ) = HoldingEntry(
        stockId = stockId,
        shares = BigDecimal(shares),
        avgPrice = BigDecimal(avgPrice),
        currency = currency,
        purchasedAt = LocalDate.of(2026, 1, 2),
    )
}

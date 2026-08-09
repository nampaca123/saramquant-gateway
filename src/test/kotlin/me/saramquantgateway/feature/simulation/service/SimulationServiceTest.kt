package me.saramquantgateway.feature.simulation.service

import me.saramquantgateway.domain.document.HoldingEntry
import me.saramquantgateway.domain.document.PortfolioEntry
import me.saramquantgateway.feature.portfolio.service.CalcPortfolioRequestBuilder
import me.saramquantgateway.feature.portfolio.service.PortfolioService
import me.saramquantgateway.infra.connection.CalcServerClient
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class SimulationServiceTest {

    private val userId: UUID = UUID.randomUUID()
    private val portfolioId = 42L
    private val params = mapOf("days" to "30", "simulations" to "1000")

    private lateinit var calcClient: CalcServerClient
    private lateinit var portfolioService: PortfolioService
    private lateinit var requestBuilder: CalcPortfolioRequestBuilder
    private lateinit var service: SimulationService

    @BeforeEach
    fun setUp() {
        calcClient = Mockito.mock(CalcServerClient::class.java)
        portfolioService = Mockito.mock(PortfolioService::class.java)
        requestBuilder = Mockito.mock(CalcPortfolioRequestBuilder::class.java)
        service = SimulationService(calcClient, portfolioService, requestBuilder)
    }

    @Test
    fun `stock simulation still uses the symbol path`() {
        Mockito.`when`(calcClient.get("/internal/stocks/AAPL/simulation", params)).thenReturn(mapOf("ok" to true))

        val result = service.runStockSimulation("AAPL", params)

        assertEquals(mapOf("ok" to true), result)
    }

    @Test
    fun `portfolio simulation posts holdings to the id-less path`() {
        val holdings = mutableListOf(holding(7))
        val entry = PortfolioEntry(id = portfolioId, marketGroup = "KR", holdings = holdings)
        val body = mapOf("market_group" to "KR", "holdings" to listOf(mapOf("symbol" to "TEST7")))
        Mockito.`when`(portfolioService.verifyOwnership(portfolioId, userId)).thenReturn(entry)
        Mockito.`when`(requestBuilder.build("KR", holdings.toList())).thenReturn(body)
        Mockito.`when`(calcClient.post("/internal/portfolios/simulation", body, params)).thenReturn(mapOf("ok" to true))

        val result = service.runPortfolioSimulation(portfolioId, userId, params)

        assertEquals(mapOf("ok" to true), result)
        Mockito.verify(calcClient).post("/internal/portfolios/simulation", body, params)
    }

    @Test
    fun `portfolio simulation still calls calc for an empty portfolio`() {
        val entry = PortfolioEntry(id = portfolioId, marketGroup = "US")
        val body = mapOf("market_group" to "US", "holdings" to emptyList<Any>())
        Mockito.`when`(portfolioService.verifyOwnership(portfolioId, userId)).thenReturn(entry)
        Mockito.`when`(requestBuilder.build("US", emptyList())).thenReturn(body)
        Mockito.`when`(calcClient.post("/internal/portfolios/simulation", body, params)).thenReturn(null)

        val result = service.runPortfolioSimulation(portfolioId, userId, params)

        assertNull(result)
        Mockito.verify(calcClient).post("/internal/portfolios/simulation", body, params)
    }

    private fun holding(stockId: Long) = HoldingEntry(
        stockId = stockId,
        shares = BigDecimal("10"),
        avgPrice = BigDecimal("1000"),
        currency = "KRW",
        purchasedAt = LocalDate.of(2026, 1, 2),
    )
}

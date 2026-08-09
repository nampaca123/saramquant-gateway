package me.saramquantgateway.feature.portfolio.controller

import me.saramquantgateway.domain.document.HoldingEntry
import me.saramquantgateway.domain.document.PortfolioEntry
import me.saramquantgateway.domain.store.LlmCacheStore
import me.saramquantgateway.feature.portfolio.service.CalcPortfolioRequestBuilder
import me.saramquantgateway.feature.portfolio.service.PortfolioService
import me.saramquantgateway.infra.connection.CalcServerClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class PortfolioAnalysisTest {

    private val userId: UUID = UUID.randomUUID()
    private val portfolioId = 42L

    private lateinit var portfolioService: PortfolioService
    private lateinit var calcClient: CalcServerClient
    private lateinit var llmCacheStore: LlmCacheStore
    private lateinit var requestBuilder: CalcPortfolioRequestBuilder
    private lateinit var controller: PortfolioController

    @BeforeEach
    fun setUp() {
        portfolioService = Mockito.mock(PortfolioService::class.java)
        calcClient = Mockito.mock(CalcServerClient::class.java)
        llmCacheStore = Mockito.mock(LlmCacheStore::class.java)
        requestBuilder = Mockito.mock(CalcPortfolioRequestBuilder::class.java)
        controller = PortfolioController(portfolioService, calcClient, llmCacheStore, requestBuilder)
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(userId.toString(), null, emptyList())
    }

    @AfterEach
    fun tearDown() = SecurityContextHolder.clearContext()

    @Test
    fun `analysis posts market group and holdings to full-analysis`() {
        val holdings = mutableListOf(holding(7))
        val entry = PortfolioEntry(id = portfolioId, marketGroup = "KR", holdings = holdings)
        val body = mapOf("market_group" to "KR", "holdings" to listOf(mapOf("symbol" to "TEST7")))
        Mockito.`when`(portfolioService.verifyOwnership(portfolioId, userId)).thenReturn(entry)
        Mockito.`when`(requestBuilder.build("KR", holdings.toList())).thenReturn(body)
        Mockito.`when`(calcClient.post("/internal/portfolios/full-analysis", body, emptyMap()))
            .thenReturn(mapOf("risk_score" to mapOf("score" to 30.0)))

        val response = controller.analysis(portfolioId)

        Mockito.verify(calcClient).post("/internal/portfolios/full-analysis", body, emptyMap())
        @Suppress("UNCHECKED_CAST")
        val payload = response.body as Map<String, Any?>
        assertEquals(70.0, (payload["risk_score"] as Map<*, *>)["score"])
    }

    @Test
    fun `analysis returns an empty map when calc has nothing for an empty portfolio`() {
        val entry = PortfolioEntry(id = portfolioId, marketGroup = "US")
        val body = mapOf("market_group" to "US", "holdings" to emptyList<Any>())
        Mockito.`when`(portfolioService.verifyOwnership(portfolioId, userId)).thenReturn(entry)
        Mockito.`when`(requestBuilder.build("US", emptyList())).thenReturn(body)
        Mockito.`when`(calcClient.post("/internal/portfolios/full-analysis", body, emptyMap())).thenReturn(null)

        val response = controller.analysis(portfolioId)

        assertEquals(emptyMap<String, Any>(), response.body)
        Mockito.verify(calcClient).post("/internal/portfolios/full-analysis", body, emptyMap())
    }

    private fun holding(stockId: Long) = HoldingEntry(
        stockId = stockId,
        shares = BigDecimal("10"),
        avgPrice = BigDecimal("1000"),
        currency = "KRW",
        purchasedAt = LocalDate.of(2026, 1, 2),
    )
}

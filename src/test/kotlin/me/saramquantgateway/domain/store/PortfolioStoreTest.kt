package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.HoldingEntry
import me.saramquantgateway.domain.document.PortfolioDoc
import me.saramquantgateway.domain.document.PortfolioEntry
import me.saramquantgateway.support.S3TestSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.services.s3.S3Client
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class PortfolioStoreTest {

    @Test
    fun `findByUserId returns null for an unknown user`() {
        assertNull(store.findByUserId(UUID.randomUUID()))
    }

    @Test
    fun `save then findByUserId roundtrips both portfolios and holdings`() {
        val userId = UUID.randomUUID()
        val doc = newDoc(userId)
        doc.portfolios[0].holdings += holding(stockId = 7)

        store.save(doc)
        val loaded = store.findByUserId(userId)!!

        assertEquals(2, loaded.portfolios.size)
        assertEquals(setOf("KR", "US"), loaded.portfolios.map { it.marketGroup }.toSet())
        val kr = loaded.portfolios.first { it.marketGroup == "KR" }
        assertEquals(PortfolioStore.portfolioIdOf(userId, "KR"), kr.id)
        assertEquals(1, kr.holdings.size)
        assertEquals(7L, kr.holdings[0].stockId)
        assertEquals(0, BigDecimal("10").compareTo(kr.holdings[0].shares))
        assertEquals(0, BigDecimal("1000.0000").compareTo(kr.holdings[0].avgPrice))
        assertEquals(LocalDate.of(2026, 1, 2), kr.holdings[0].purchasedAt)
        assertEquals("KRW", kr.holdings[0].currency)
    }

    @Test
    fun `findEntry resolves a single portfolio by id`() {
        val userId = UUID.randomUUID()
        store.save(newDoc(userId))

        val entry = store.findEntry(userId, PortfolioStore.portfolioIdOf(userId, "US"))

        assertNotNull(entry)
        assertEquals("US", entry!!.marketGroup)
    }

    @Test
    fun `findEntry returns null for a portfolio id owned by someone else`() {
        val userId = UUID.randomUUID()
        store.save(newDoc(userId))

        assertNull(store.findEntry(userId, PortfolioStore.portfolioIdOf(UUID.randomUUID(), "KR")))
    }

    @Test
    fun `buy then sell then reset scenario survives reload`() {
        val userId = UUID.randomUUID()
        store.save(newDoc(userId))
        val krId = PortfolioStore.portfolioIdOf(userId, "KR")

        // buy 10주
        store.findByUserId(userId)!!.let { doc ->
            doc.portfolios.first { it.id == krId }.holdings += holding(stockId = 7)
            store.save(doc)
        }
        assertEquals(1, store.findEntry(userId, krId)!!.holdings.size)

        // sell 4주 (부분)
        store.findByUserId(userId)!!.let { doc ->
            val h = doc.portfolios.first { it.id == krId }.holdings[0]
            h.shares = h.shares.subtract(BigDecimal("4"))
            store.save(doc)
        }
        assertEquals(0, BigDecimal("6").compareTo(store.findEntry(userId, krId)!!.holdings[0].shares))

        // reset
        store.findByUserId(userId)!!.let { doc ->
            doc.portfolios.first { it.id == krId }.holdings.clear()
            store.save(doc)
        }
        val after = store.findByUserId(userId)!!
        assertTrue(after.portfolios.first { it.id == krId }.holdings.isEmpty())
        assertEquals(2, after.portfolios.size)
    }

    companion object {
        private val prefix = S3TestSupport.newPrefix()
        private lateinit var s3: S3Client
        private lateinit var store: PortfolioStore

        private fun newDoc(userId: UUID) = PortfolioDoc(
            userId = userId,
            portfolios = mutableListOf(
                PortfolioEntry(id = PortfolioStore.portfolioIdOf(userId, "KR"), marketGroup = "KR"),
                PortfolioEntry(id = PortfolioStore.portfolioIdOf(userId, "US"), marketGroup = "US"),
            ),
        )

        private fun holding(stockId: Long) = HoldingEntry(
            stockId = stockId,
            shares = BigDecimal("10"),
            avgPrice = BigDecimal("1000.0000"),
            currency = "KRW",
            purchasedAt = LocalDate.of(2026, 1, 2),
        )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            store = PortfolioStore(S3TestSupport.newKvStore(s3, prefix))
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            S3TestSupport.cleanup(s3, prefix)
            s3.close()
        }
    }
}

package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.LlmAnalysisDoc
import me.saramquantgateway.support.S3TestSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.services.s3.S3Client
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.random.Random

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class LlmCacheStoreTest {

    @Test
    fun `findStock returns null when nothing cached`() {
        assertNull(store.findStock(newId(), TODAY, "summary", "ko"))
    }

    @Test
    fun `saveStock then findStock roundtrips the document`() {
        val stockId = newId()
        store.saveStock(doc(stockId, analysis = "stock analysis"))

        val loaded = store.findStock(stockId, TODAY, "summary", "ko")!!

        assertEquals(stockId, loaded.targetId)
        assertEquals(TODAY, loaded.date)
        assertEquals("summary", loaded.preset)
        assertEquals("ko", loaded.lang)
        assertEquals("stock analysis", loaded.analysis)
        assertEquals("claude-test", loaded.model)
    }

    @Test
    fun `stock cache is keyed by date preset and lang`() {
        val stockId = newId()
        store.saveStock(doc(stockId, preset = "summary", lang = "ko"))

        assertNull(store.findStock(stockId, TODAY, "summary", "en"))
        assertNull(store.findStock(stockId, TODAY, "deep", "ko"))
        assertNull(store.findStock(stockId, TODAY.minusDays(1), "summary", "ko"))
    }

    @Test
    fun `savePortfolio then findPortfolio roundtrips and does not collide with stock cache`() {
        val id = newId()
        store.saveStock(doc(id, analysis = "stock"))
        store.savePortfolio(doc(id, analysis = "portfolio"))

        assertEquals("stock", store.findStock(id, TODAY, "summary", "ko")!!.analysis)
        assertEquals("portfolio", store.findPortfolio(id, TODAY, "summary", "ko")!!.analysis)
    }

    @Test
    fun `listPortfolioHistory returns documents newest first`() {
        val portfolioId = newId()
        val base = Instant.parse("2026-08-01T00:00:00Z")
        store.savePortfolio(doc(portfolioId, preset = "a", createdAt = base))
        store.savePortfolio(doc(portfolioId, preset = "b", createdAt = base.plusSeconds(60)))
        store.savePortfolio(doc(portfolioId, preset = "c", createdAt = base.plusSeconds(30)))

        val history = store.listPortfolioHistory(portfolioId)

        assertEquals(listOf("b", "c", "a"), history.map { it.preset })
    }

    @Test
    fun `deletePortfolioAll removes only that portfolio history`() {
        val kept = newId()
        val removed = newId()
        store.savePortfolio(doc(kept))
        store.savePortfolio(doc(removed, preset = "a"))
        store.savePortfolio(doc(removed, preset = "b"))

        store.deletePortfolioAll(removed)

        assertTrue(store.listPortfolioHistory(removed).isEmpty())
        assertEquals(1, store.listPortfolioHistory(kept).size)
    }

    @Test
    fun `deleteOlderThan removes stock and portfolio entries older than the cutoff`() {
        val cleanup = LlmCacheStore(S3TestSupport.newKvStore(s3, "$prefix$CLEANUP_SUB"))
        val stockId = newId()
        val portfolioId = newId()
        cleanup.saveStock(doc(stockId))
        cleanup.savePortfolio(doc(portfolioId))

        assertEquals(0, cleanup.deleteOlderThan(Instant.now().minus(1, ChronoUnit.DAYS)))

        val deleted = cleanup.deleteOlderThan(Instant.now().plus(1, ChronoUnit.DAYS))

        assertEquals(2, deleted)
        assertNull(cleanup.findStock(stockId, TODAY, "summary", "ko"))
        assertNull(cleanup.findPortfolio(portfolioId, TODAY, "summary", "ko"))
    }

    companion object {
        private const val CLEANUP_SUB = "cleanup/"
        private val TODAY: LocalDate = LocalDate.of(2026, 8, 9)
        private val prefix = S3TestSupport.newPrefix()
        private lateinit var s3: S3Client
        private lateinit var store: LlmCacheStore

        private fun newId(): Long = Random.nextLong(1, Long.MAX_VALUE)

        private fun doc(
            targetId: Long,
            preset: String = "summary",
            lang: String = "ko",
            analysis: String = "text",
            createdAt: Instant = Instant.now(),
        ) = LlmAnalysisDoc(
            targetId = targetId, date = TODAY, preset = preset, lang = lang,
            analysis = analysis, model = "claude-test", createdAt = createdAt,
        )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            store = LlmCacheStore(S3TestSupport.newKvStore(s3, prefix))
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            S3TestSupport.cleanup(s3, prefix)
            s3.close()
        }
    }
}

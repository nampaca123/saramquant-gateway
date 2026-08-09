package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.RecommendationDoc
import me.saramquantgateway.support.S3TestSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.services.s3.S3Client
import java.time.Instant
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class RecommendationStoreTest {

    @Test
    fun `findPage is empty for a user without recommendations`() {
        val page = store.findPage(UUID.randomUUID(), "KR", 0, 10)

        assertTrue(page.content.isEmpty())
        assertEquals(0L, page.totalElements)
        assertFalse(page.hasNext)
    }

    @Test
    fun `findPage returns newest first and reports hasNext`() {
        val userId = UUID.randomUUID()
        store.save(doc(userId, "KR", "oldest", BASE))
        store.save(doc(userId, "KR", "newest", BASE.plusSeconds(120)))
        store.save(doc(userId, "KR", "middle", BASE.plusSeconds(60)))

        val first = store.findPage(userId, "KR", 0, 2)

        assertEquals(listOf("newest", "middle"), first.content.map { it.reasoning })
        assertEquals(3L, first.totalElements)
        assertTrue(first.hasNext)

        val second = store.findPage(userId, "KR", 1, 2)

        assertEquals(listOf("oldest"), second.content.map { it.reasoning })
        assertEquals(3L, second.totalElements)
        assertFalse(second.hasNext)
    }

    @Test
    fun `findPage isolates market groups`() {
        val userId = UUID.randomUUID()
        store.save(doc(userId, "KR", "kr", BASE))
        store.save(doc(userId, "US", "us", BASE.plusSeconds(60)))

        assertEquals(listOf("kr"), store.findPage(userId, "KR", 0, 10).content.map { it.reasoning })
        assertEquals(listOf("us"), store.findPage(userId, "US", 0, 10).content.map { it.reasoning })
    }

    @Test
    fun `save roundtrips every field`() {
        val userId = UUID.randomUUID()
        store.save(doc(userId, "KR", "why", BASE))

        val loaded = store.findPage(userId, "KR", 0, 10).content.single()

        assertEquals(userId, loaded.userId)
        assertEquals("KR", loaded.marketGroup)
        assertEquals("ASSESSED", loaded.riskTolerance)
        assertEquals("ko", loaded.lang)
        assertEquals("[]", loaded.stocks)
        assertEquals("why", loaded.reasoning)
        assertEquals("claude-test", loaded.model)
        assertEquals(BASE, loaded.createdAt)
    }

    companion object {
        private val BASE: Instant = Instant.parse("2026-08-01T00:00:00Z")
        private val prefix = S3TestSupport.newPrefix()
        private lateinit var s3: S3Client
        private lateinit var store: RecommendationStore

        private fun doc(userId: UUID, marketGroup: String, reasoning: String, createdAt: Instant) =
            RecommendationDoc(
                userId = userId, marketGroup = marketGroup, riskTolerance = "ASSESSED", lang = "ko",
                stocks = "[]", reasoning = reasoning, model = "claude-test", createdAt = createdAt,
            )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            store = RecommendationStore(S3TestSupport.newKvStore(s3, prefix))
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            S3TestSupport.cleanup(s3, prefix)
            s3.close()
        }
    }
}

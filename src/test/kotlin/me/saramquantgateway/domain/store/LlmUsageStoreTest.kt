package me.saramquantgateway.domain.store

import me.saramquantgateway.support.S3TestSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.services.s3.S3Client
import java.time.LocalDate
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class LlmUsageStoreTest {

    @Test
    fun `getCount is zero for an untouched user and date`() {
        assertEquals(0, store.getCount(UUID.randomUUID(), DATE))
    }

    @Test
    fun `incrementBy accumulates and decrementBy floors at zero`() {
        val userId = UUID.randomUUID()

        assertEquals(1, store.incrementBy(userId, DATE, 1))
        assertEquals(4, store.incrementBy(userId, DATE, 3))
        assertEquals(4, store.getCount(userId, DATE))

        assertEquals(1, store.decrementBy(userId, DATE, 3))
        assertEquals(0, store.decrementBy(userId, DATE, 9))
        assertEquals(0, store.getCount(userId, DATE))
    }

    @Test
    fun `counts are isolated per date`() {
        val userId = UUID.randomUUID()
        store.incrementBy(userId, DATE, 2)

        assertEquals(0, store.getCount(userId, DATE.plusDays(1)))
    }

    companion object {
        private val DATE: LocalDate = LocalDate.of(2026, 8, 9)
        private val prefix = S3TestSupport.newPrefix()
        private lateinit var s3: S3Client
        private lateinit var store: LlmUsageStore

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            store = LlmUsageStore(S3TestSupport.newKvStore(s3, prefix))
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            S3TestSupport.cleanup(s3, prefix)
            s3.close()
        }
    }
}

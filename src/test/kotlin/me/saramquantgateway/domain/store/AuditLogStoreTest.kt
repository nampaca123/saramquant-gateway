package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.AuditLogDoc
import me.saramquantgateway.infra.duckdb.DuckDbConfig
import me.saramquantgateway.infra.duckdb.DuckDbQueryExecutor
import me.saramquantgateway.infra.storage.s3.S3StorageProperties
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
import java.time.LocalDate
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class AuditLogStoreTest {

    @Test
    fun `append then findFiltered roundtrips every field of the same day`() {
        val page = store.findFiltered(null, null, day(1), day(2), 0, 10)

        val doc = page.content.single()
        assertEquals(ROUNDTRIP_ID, doc.id)
        assertEquals("gateway", doc.server)
        assertEquals("API", doc.action)
        assertEquals("GET", doc.method)
        assertEquals("/api/stocks", doc.path)
        assertEquals("1.2.*.*", doc.ipMasked)
        assertEquals(USER_ID, doc.userId)
        assertEquals(200, doc.statusCode)
        assertEquals(42L, doc.durationMs)
        assertEquals("""{"k":"v"}""", doc.metadata)
        assertEquals(day(1).plusSeconds(3600), doc.createdAt)
        assertEquals(1L, page.totalElements)
        assertFalse(page.hasNext)
    }

    @Test
    fun `findFiltered filters by action and server`() {
        val emails = store.findFiltered(null, "EMAIL", day(2), day(3), 0, 10)
        val all = store.findFiltered("gateway", null, day(2), day(3), 0, 10)
        val other = store.findFiltered("calc", null, day(2), day(3), 0, 10)

        assertEquals(listOf("EMAIL"), emails.content.map { it.action })
        assertEquals(1L, emails.totalElements)
        assertEquals(2, all.content.size)
        assertTrue(other.content.isEmpty())
    }

    @Test
    fun `findFiltered paginates newest first`() {
        val first = store.findFiltered(null, null, day(3), day(4), 0, 2)
        val second = store.findFiltered(null, null, day(3), day(4), 1, 2)

        assertEquals(listOf("/p3", "/p2"), first.content.map { it.path })
        assertEquals(3L, first.totalElements)
        assertTrue(first.hasNext)
        assertEquals(listOf("/p1"), second.content.map { it.path })
        assertFalse(second.hasNext)
    }

    @Test
    fun `findFiltered spans several day partitions`() {
        val page = store.findFiltered(null, null, day(31), day(33), 0, 10)

        assertEquals(listOf("/sep2", "/sep1"), page.content.map { it.path })
        assertEquals(2L, page.totalElements)
    }

    @Test
    fun `findFiltered is empty when no partition exists in the range`() {
        val page = store.findFiltered(null, null, Instant.parse("2020-01-01T00:00:00Z"), Instant.parse("2020-01-05T00:00:00Z"), 0, 10)

        assertTrue(page.content.isEmpty())
        assertEquals(0L, page.totalElements)
        assertFalse(page.hasNext)
    }

    @Test
    fun `countVisitors returns distinct visit rows in KST`() {
        val stats = store.countVisitors(day(4), day(5))

        assertEquals(setOf("1.2.*.*", "3.4.*.*"), stats.map { it.ipMasked }.toSet())
        assertEquals(3, stats.size)
        assertEquals(setOf(LocalDate.of(2026, 8, 4)), stats.map { it.visitDate }.toSet())
        assertEquals(setOf(10, 11), stats.map { it.hour }.toSet())
    }

    companion object {
        private val ROUNDTRIP_ID: UUID = UUID.randomUUID()
        private val USER_ID: UUID = UUID.randomUUID()
        private val prefix = S3TestSupport.newPrefix()
        private lateinit var s3: S3Client
        private lateinit var duckDb: DuckDbQueryExecutor
        private lateinit var store: AuditLogStore

        // 2026-08-01 기준 n일차 자정 UTC
        private fun day(n: Int): Instant = Instant.parse("2026-08-01T00:00:00Z").plusSeconds((n - 1) * 86400L)

        private fun doc(
            at: Instant,
            action: String = "API",
            path: String? = null,
            ipMasked: String? = null,
            id: UUID = UUID.randomUUID(),
        ) = AuditLogDoc(
            id = id, server = "gateway", action = action, method = "GET", path = path,
            ipMasked = ipMasked, userId = null, statusCode = 200, durationMs = 42,
            metadata = null, createdAt = at,
        )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            duckDb = DuckDbQueryExecutor(DuckDbConfig.openInitializedConnection(S3TestSupport.REGION))
            store = AuditLogStore(
                S3TestSupport.newKvStore(s3, prefix),
                duckDb,
                S3StorageProperties(S3TestSupport.BUCKET, prefix, S3TestSupport.REGION),
            )

            store.append(
                AuditLogDoc(
                    id = ROUNDTRIP_ID, server = "gateway", action = "API", method = "GET",
                    path = "/api/stocks", ipMasked = "1.2.*.*", userId = USER_ID, statusCode = 200,
                    durationMs = 42, metadata = """{"k":"v"}""", createdAt = day(1).plusSeconds(3600),
                ),
            )
            store.append(doc(day(2).plusSeconds(60), action = "API"))
            store.append(doc(day(2).plusSeconds(120), action = "EMAIL"))
            store.append(doc(day(3).plusSeconds(60), path = "/p1"))
            store.append(doc(day(3).plusSeconds(120), path = "/p2"))
            store.append(doc(day(3).plusSeconds(180), path = "/p3"))
            store.append(doc(day(4).plusSeconds(3600), path = "/v", ipMasked = "1.2.*.*"))
            store.append(doc(day(4).plusSeconds(3660), path = "/v", ipMasked = "1.2.*.*"))
            store.append(doc(day(4).plusSeconds(7200), path = "/v", ipMasked = "1.2.*.*"))
            store.append(doc(day(4).plusSeconds(7260), path = "/v", ipMasked = "3.4.*.*"))
            store.append(doc(day(31).plusSeconds(60), path = "/sep1"))
            store.append(doc(day(32).plusSeconds(60), path = "/sep2"))
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            duckDb.close()
            S3TestSupport.cleanup(s3, prefix)
            s3.close()
        }
    }
}

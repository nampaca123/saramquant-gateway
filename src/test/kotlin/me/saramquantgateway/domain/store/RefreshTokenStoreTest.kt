package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.RefreshTokenDoc
import me.saramquantgateway.support.S3TestSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.services.s3.S3Client
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class RefreshTokenStoreTest {

    @Test
    fun `save then findByTokenHash roundtrips`() {
        val doc = newToken(UUID.randomUUID(), "hash-roundtrip")
        store.save(doc)

        val loaded = store.findByTokenHash("hash-roundtrip")!!

        assertEquals(doc.id, loaded.id)
        assertEquals(doc.userId, loaded.userId)
        assertNull(loaded.revokedAt)
    }

    @Test
    fun `findByTokenHash returns null for unknown hash`() {
        assertNull(store.findByTokenHash("no-such-hash"))
    }

    @Test
    fun `revokeAllByUserId marks every active token of the user`() {
        val userId = UUID.randomUUID()
        val other = UUID.randomUUID()
        store.save(newToken(userId, "revoke-a"))
        store.save(newToken(userId, "revoke-b"))
        store.save(newToken(other, "revoke-other"))
        val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        store.revokeAllByUserId(userId, now)

        assertEquals(now, store.findByTokenHash("revoke-a")!!.revokedAt)
        assertEquals(now, store.findByTokenHash("revoke-b")!!.revokedAt)
        assertNull(store.findByTokenHash("revoke-other")!!.revokedAt)
    }

    @Test
    fun `revokeAllByUserId keeps the original revocation time`() {
        val userId = UUID.randomUUID()
        val earlier = Instant.now().minusSeconds(600).truncatedTo(ChronoUnit.MILLIS)
        store.save(newToken(userId, "already-revoked").apply { revokedAt = earlier })

        store.revokeAllByUserId(userId, Instant.now())

        assertEquals(earlier, store.findByTokenHash("already-revoked")!!.revokedAt)
    }

    @Test
    fun `deleteExpired removes docs older than the refresh ttl`() {
        val userId = UUID.randomUUID()
        zeroTtlStore.save(newToken(userId, "expired-one"))
        assertNotNull(zeroTtlStore.findByTokenHash("expired-one"))

        val deleted = zeroTtlStore.deleteExpired(Instant.now().plusSeconds(60))

        assertEquals(1, deleted)
        assertNull(zeroTtlStore.findByTokenHash("expired-one"))
    }

    @Test
    fun `deleteExpired keeps fresh docs`() {
        store.save(newToken(UUID.randomUUID(), "fresh-one"))

        val deleted = store.deleteExpired(Instant.now())

        assertEquals(0, deleted)
        assertNotNull(store.findByTokenHash("fresh-one"))
    }

    companion object {
        private val prefix = S3TestSupport.newPrefix()
        private val zeroTtlPrefix = S3TestSupport.newPrefix()
        private lateinit var s3: S3Client
        private lateinit var store: RefreshTokenStore
        private lateinit var zeroTtlStore: RefreshTokenStore

        private fun newToken(userId: UUID, hash: String) = RefreshTokenDoc(
            userId = userId,
            tokenHash = hash,
            expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS),
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            store = RefreshTokenStore(S3TestSupport.newKvStore(s3, prefix), 1_209_600L)
            zeroTtlStore = RefreshTokenStore(S3TestSupport.newKvStore(s3, zeroTtlPrefix), 0L)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            S3TestSupport.cleanup(s3, prefix)
            S3TestSupport.cleanup(s3, zeroTtlPrefix)
            s3.close()
        }
    }
}

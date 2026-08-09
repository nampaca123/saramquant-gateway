package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.EmailVerificationDoc
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
class EmailVerificationStoreTest {

    @Test
    fun `save keeps only the latest doc per purpose and email`() {
        val hash = "hash-latest"
        val first = newDoc(hash, "SIGNUP", "11111")
        val second = newDoc(hash, "SIGNUP", "22222")

        store.save(first)
        store.save(second)

        val latest = store.findLatest(hash, "SIGNUP")!!
        assertEquals(second.id, latest.id)
        assertEquals("22222", latest.code)
    }

    @Test
    fun `findLatest isolates purposes`() {
        val hash = "hash-purpose"
        store.save(newDoc(hash, "SIGNUP", "33333"))
        store.save(newDoc(hash, "PASSWORD_RESET", "44444"))

        assertEquals("33333", store.findLatest(hash, "SIGNUP")!!.code)
        assertEquals("44444", store.findLatest(hash, "PASSWORD_RESET")!!.code)
        assertNull(store.findLatest("hash-unknown", "SIGNUP"))
    }

    @Test
    fun `findVerified requires matching id and verified flag`() {
        val hash = "hash-verified"
        val doc = newDoc(hash, "SIGNUP", "55555")
        store.save(doc)

        assertNull(store.findVerified(doc.id, hash, "SIGNUP"))

        doc.verified = true
        doc.verifiedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)
        store.save(doc)

        assertNotNull(store.findVerified(doc.id, hash, "SIGNUP"))
        assertNull(store.findVerified(UUID.randomUUID(), hash, "SIGNUP"))
    }

    @Test
    fun `deleteExpired removes docs untouched before the cutoff`() {
        cleanupStore.save(newDoc("hash-cleanup", "SIGNUP", "66666"))

        assertEquals(0, cleanupStore.deleteExpired(Instant.now().minusSeconds(3600)))
        assertEquals(1, cleanupStore.deleteExpired(Instant.now().plusSeconds(60)))
        assertNull(cleanupStore.findLatest("hash-cleanup", "SIGNUP"))
    }

    companion object {
        private val prefix = S3TestSupport.newPrefix()
        private val cleanupPrefix = S3TestSupport.newPrefix()
        private lateinit var s3: S3Client
        private lateinit var store: EmailVerificationStore
        private lateinit var cleanupStore: EmailVerificationStore

        private fun newDoc(emailHash: String, purpose: String, code: String) = EmailVerificationDoc(
            emailHash = emailHash,
            purpose = purpose,
            code = code,
            expiresAt = Instant.now().plusSeconds(3600).truncatedTo(ChronoUnit.MILLIS),
            createdAt = Instant.now().truncatedTo(ChronoUnit.MILLIS),
        )

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            store = EmailVerificationStore(S3TestSupport.newKvStore(s3, prefix))
            cleanupStore = EmailVerificationStore(S3TestSupport.newKvStore(s3, cleanupPrefix))
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            S3TestSupport.cleanup(s3, prefix)
            S3TestSupport.cleanup(s3, cleanupPrefix)
            s3.close()
        }
    }
}

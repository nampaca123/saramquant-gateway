package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.UserDoc
import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.enum.user.InvestmentExperience
import me.saramquantgateway.infra.security.crypto.AesEncryptor
import me.saramquantgateway.infra.security.crypto.CryptoProperties
import me.saramquantgateway.support.S3TestSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.services.s3.S3Client
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class UserStoreTest {

    @Test
    fun `create then findById roundtrips all fields`() {
        val doc = newUser("roundtrip@example.com").apply {
            nickname = "quantking"
            birthYear = 1993
            investmentExperience = InvestmentExperience.ADVANCED
            preferredMarkets = mutableSetOf(Market.KR_KOSPI, Market.US_NASDAQ)
        }

        assertTrue(store.create(doc))
        val loaded = store.findById(doc.id)!!

        assertEquals("roundtrip@example.com", loaded.email)
        assertEquals("quantking", loaded.nickname)
        assertEquals(doc.name, loaded.name)
        assertEquals(doc.providerId, loaded.providerId)
        assertEquals(setOf(Market.KR_KOSPI, Market.US_NASDAQ), loaded.preferredMarkets)
        assertEquals(InvestmentExperience.ADVANCED, loaded.investmentExperience)
    }

    @Test
    fun `create returns false when email pointer already taken`() {
        val first = newUser("race@example.com")
        val second = newUser("race@example.com")

        assertTrue(store.create(first))
        assertFalse(store.create(second))
        assertNull(store.findById(second.id))
        assertEquals(first.id, store.findByEmailHash(first.emailHash)!!.id)
    }

    @Test
    fun `findByEmailHash resolves through pointer`() {
        val doc = newUser("pointer@example.com")
        store.create(doc)

        val loaded = store.findByEmailHash(doc.emailHash)!!

        assertEquals(doc.id, loaded.id)
        assertEquals("pointer@example.com", loaded.email)
    }

    @Test
    fun `findByEmailHash returns null for unknown hash`() {
        assertNull(store.findByEmailHash("deadbeef"))
    }

    @Test
    fun `save updates existing doc`() {
        val doc = newUser("save@example.com")
        store.create(doc)

        doc.isActive = false
        doc.nickname = "renamed"
        store.save(doc)

        val loaded = store.findById(doc.id)!!
        assertFalse(loaded.isActive)
        assertEquals("renamed", loaded.nickname)
        assertEquals("save@example.com", loaded.email)
    }

    @Test
    fun `pii is never stored as plaintext in raw json`() {
        val doc = newUser("secret-pii@example.com").apply { nickname = "secretnick" }
        store.create(doc)

        val raw = S3TestSupport.rawJson(s3, prefix, "users/${doc.id}.json")

        assertFalse(raw.contains("secret-pii@example.com"))
        assertFalse(raw.contains("secretnick"))
        assertFalse(raw.contains(doc.name))
        assertTrue(raw.contains(doc.emailHash))
    }

    companion object {
        private val prefix = S3TestSupport.newPrefix()
        private lateinit var s3: S3Client
        private lateinit var store: UserStore
        private lateinit var aes: AesEncryptor

        private fun newUser(email: String): UserDoc {
            val id = UUID.randomUUID()
            return UserDoc(
                id = id,
                email = email,
                emailHash = "h" + email.hashCode().toString(16),
                name = "Tester $id",
                provider = AuthProvider.MANUAL,
                providerId = email,
            )
        }

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            aes = AesEncryptor(CryptoProperties("unit-test-secret"))
            store = UserStore(S3TestSupport.newKvStore(s3, prefix), aes)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            S3TestSupport.cleanup(s3, prefix)
            s3.close()
        }
    }
}

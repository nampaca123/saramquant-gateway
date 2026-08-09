package me.saramquantgateway.infra.storage.s3

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.Delete
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request
import software.amazon.awssdk.services.s3.model.ObjectIdentifier
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class TestDoc(val id: String, val createdAt: Instant)

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class S3KvStoreTest {

    @Test
    fun `put then get roundtrips including instant field`() {
        val doc = TestDoc("a", now)
        store.put("roundtrip/a.json", doc)

        val loaded = store.get("roundtrip/a.json", TestDoc::class.java)

        assertEquals(doc, loaded)
    }

    @Test
    fun `get returns null when key is absent`() {
        assertNull(store.get("missing/none.json", TestDoc::class.java))
    }

    @Test
    fun `putIfAbsent returns true first and false when key exists`() {
        val key = "ifabsent/b.json"

        assertTrue(store.putIfAbsent(key, TestDoc("first", now)))
        assertFalse(store.putIfAbsent(key, TestDoc("second", now)))
        assertEquals("first", store.get(key, TestDoc::class.java)!!.id)
    }

    @Test
    fun `delete removes key and is idempotent`() {
        val key = "delete/c.json"
        store.put(key, TestDoc("c", now))

        store.delete(key)
        store.delete(key)

        assertNull(store.get(key, TestDoc::class.java))
    }

    @Test
    fun `listKeys returns logical keys under prefix only`() {
        store.put("listing/one.json", TestDoc("one", now))
        store.put("listing/two.json", TestDoc("two", now))
        store.put("other/three.json", TestDoc("three", now))

        val keys = store.listKeys("listing/")

        assertEquals(listOf("listing/one.json", "listing/two.json"), keys.sorted())
    }

    @Test
    fun `listEntries returns keys with last modified`() {
        store.put("entries/one.json", TestDoc("one", now))

        val entries = store.listEntries("entries/")

        assertEquals(listOf("entries/one.json"), entries.map { it.key })
        assertTrue(entries.single().lastModified.isAfter(Instant.now().minus(10, ChronoUnit.MINUTES)))
    }

    companion object {
        private const val BUCKET = "saramquant-bucket"
        private const val REGION = "ap-northeast-2"
        private val testPrefix = "app-test/${UUID.randomUUID()}/"
        private val now: Instant = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        private lateinit var s3: S3Client
        private lateinit var store: S3KvStore

        @BeforeAll
        @JvmStatic
        fun setUp() {
            val credentials = AwsBasicCredentials.create(
                System.getenv("SARAMQUANT_IAM_KEY_ACCESS"),
                System.getenv("SARAMQUANT_IAM_KEY_SECRET"),
            )
            s3 = S3Client.builder()
                .region(Region.of(REGION))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()
            val objectMapper = jacksonObjectMapper()
                .registerModule(JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            store = S3KvStore(s3, S3StorageProperties(BUCKET, testPrefix, REGION), objectMapper)
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            var token: String? = null
            while (true) {
                val response = s3.listObjectsV2(
                    ListObjectsV2Request.builder()
                        .bucket(BUCKET).prefix(testPrefix).continuationToken(token).build(),
                )
                val ids = response.contents().map { ObjectIdentifier.builder().key(it.key()).build() }
                if (ids.isNotEmpty()) {
                    s3.deleteObjects(
                        DeleteObjectsRequest.builder()
                            .bucket(BUCKET).delete(Delete.builder().objects(ids).build()).build(),
                    )
                }
                if (response.isTruncated != true) break
                token = response.nextContinuationToken()
            }
            s3.close()
        }
    }
}

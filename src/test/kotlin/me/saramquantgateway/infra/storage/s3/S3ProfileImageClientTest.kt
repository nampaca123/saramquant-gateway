package me.saramquantgateway.infra.storage.s3

import me.saramquantgateway.support.S3TestSupport
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID

@EnabledIfEnvironmentVariable(named = "SARAMQUANT_IAM_KEY_ACCESS", matches = ".+")
class S3ProfileImageClientTest {

    @Test
    fun `upload returns a logical key under the profile-images prefix`() {
        val userId = UUID.randomUUID()

        val key = client.upload(userId, PNG_BYTES, "image/png")

        assertEquals("profile-images/$userId.png", key)
    }

    @Test
    fun `presigned url of an uploaded image is fetchable and returns the same bytes`() {
        val userId = UUID.randomUUID()
        val key = client.upload(userId, PNG_BYTES, "image/png")

        val response = get(client.presignedUrl(key))

        assertEquals(200, response.statusCode())
        assertArrayEquals(PNG_BYTES, response.body())
    }

    @Test
    fun `jpeg content type maps to a jpg key`() {
        val userId = UUID.randomUUID()

        val key = client.upload(userId, PNG_BYTES, "image/jpeg")

        assertEquals("profile-images/$userId.jpg", key)
        assertEquals(200, get(client.presignedUrl(key)).statusCode())
    }

    @Test
    fun `delete removes every extension and is idempotent`() {
        val userId = UUID.randomUUID()
        val pngKey = client.upload(userId, PNG_BYTES, "image/png")
        val jpgKey = client.upload(userId, PNG_BYTES, "image/jpeg")

        client.delete(userId)
        client.delete(userId)

        assertEquals(404, get(client.presignedUrl(pngKey)).statusCode())
        assertEquals(404, get(client.presignedUrl(jpgKey)).statusCode())
    }

    @Test
    fun `presigned url of a missing key is signed but resolves to 404`() {
        val url = client.presignedUrl("profile-images/${UUID.randomUUID()}.png")

        assertTrue(url.contains("X-Amz-Signature"))
        assertEquals(404, get(url).statusCode())
    }

    private fun get(url: String): HttpResponse<ByteArray> =
        http.send(
            HttpRequest.newBuilder(URI.create(url)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )

    companion object {
        private val PNG_BYTES = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x01, 0x02)
        private val http: HttpClient = HttpClient.newHttpClient()
        private val testPrefix = S3TestSupport.newPrefix()

        private lateinit var s3: S3Client
        private lateinit var presigner: S3Presigner
        private lateinit var client: S3ProfileImageClient

        @BeforeAll
        @JvmStatic
        fun setUp() {
            s3 = S3TestSupport.newClient()
            presigner = S3TestSupport.newPresigner()
            client = S3ProfileImageClient(
                s3,
                presigner,
                S3StorageProperties(S3TestSupport.BUCKET, testPrefix, S3TestSupport.REGION),
            )
        }

        @AfterAll
        @JvmStatic
        fun tearDown() {
            S3TestSupport.cleanup(s3, testPrefix)
            presigner.close()
            s3.close()
        }
    }
}

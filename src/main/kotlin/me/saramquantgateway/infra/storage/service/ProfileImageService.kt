package me.saramquantgateway.infra.storage.service

import me.saramquantgateway.infra.storage.s3.S3ProfileImageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.UUID

@Service
class ProfileImageService(private val imageClient: S3ProfileImageClient) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val rest = RestClient.create()

    fun uploadFromUrl(userId: UUID, sourceUrl: String): String? =
        try {
            val response = rest.get().uri(sourceUrl).retrieve().toEntity(ByteArray::class.java)
            val bytes = response.body ?: return sourceUrl
            imageClient.upload(userId, bytes, response.headers.contentType?.toString() ?: "image/jpeg")
        } catch (e: Exception) {
            log.warn("Profile image upload failed for user {}, falling back to provider URL", userId, e)
            sourceUrl
        }

    fun uploadFromBytes(userId: UUID, bytes: ByteArray, contentType: String): String {
        delete(userId)
        return imageClient.upload(userId, bytes, contentType)
    }

    fun delete(userId: UUID) {
        try {
            imageClient.delete(userId)
        } catch (e: Exception) {
            log.warn("Profile image delete failed for user {}", userId, e)
        }
    }

    // OAuth 폴백으로 저장된 외부 제공자 URL은 서명하지 않고 그대로 돌려준다.
    fun presignedUrl(key: String): String =
        if (key.startsWith("http")) key else imageClient.presignedUrl(key)
}

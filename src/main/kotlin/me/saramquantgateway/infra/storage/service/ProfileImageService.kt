package me.saramquantgateway.infra.storage.service

import me.saramquantgateway.infra.storage.lib.SupabaseStorageClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import java.util.UUID

@Service
class ProfileImageService(private val storage: SupabaseStorageClient) {

    private val log = LoggerFactory.getLogger(javaClass)
    private val rest = RestClient.create()

    fun uploadFromUrl(userId: UUID, sourceUrl: String): String? =
        try {
            val response = rest.get().uri(sourceUrl).retrieve().toEntity(ByteArray::class.java)
            val bytes = response.body ?: return sourceUrl
            val contentType = response.headers.contentType?.toString() ?: "image/jpeg"
            val ext = if (contentType.contains("png")) "png" else "jpg"
            storage.upload("profiles/$userId.$ext", bytes, contentType)
        } catch (e: Exception) {
            log.warn("Profile image upload failed for user {}, falling back to provider URL", userId, e)
            sourceUrl
        }

    fun uploadFromBytes(userId: UUID, bytes: ByteArray, contentType: String): String {
        delete(userId)
        val ext = if (contentType.contains("png")) "png" else "jpg"
        return storage.upload("profiles/$userId.$ext", bytes, contentType)
    }

    fun delete(userId: UUID) {
        try {
            storage.delete("profiles/$userId.jpg")
            storage.delete("profiles/$userId.png")
        } catch (_: Exception) { }
    }
}

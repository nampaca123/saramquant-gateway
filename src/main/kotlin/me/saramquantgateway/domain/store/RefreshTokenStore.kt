package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.RefreshTokenDoc
import me.saramquantgateway.infra.storage.s3.S3KvStore
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

// 본문 refresh-tokens/{hash}.json + by-user 마커로 사용자별 일괄 폐기를 지원한다.
@Component
class RefreshTokenStore(
    private val kv: S3KvStore,
    @Value("\${jwt.refresh-token-ttl}") private val refreshTtlSeconds: Long,
) {

    fun findByTokenHash(hash: String): RefreshTokenDoc? = kv.get(docKey(hash), RefreshTokenDoc::class.java)

    fun save(doc: RefreshTokenDoc) {
        kv.put(docKey(doc.tokenHash), doc)
        kv.put(markerKey(doc.userId, doc.tokenHash), TokenMarker(doc.tokenHash))
    }

    fun revokeAllByUserId(userId: UUID, now: Instant) {
        kv.listKeys(userMarkerPrefix(userId))
            .mapNotNull { findByTokenHash(it.substringAfterLast('/').removeSuffix(".json")) }
            .filter { it.revokedAt == null }
            .forEach { doc ->
                doc.revokedAt = now
                kv.put(docKey(doc.tokenHash), doc)
            }
    }

    fun deleteExpired(now: Instant): Int {
        val cutoff = now.minusSeconds(refreshTtlSeconds)
        var deleted = 0
        kv.listEntries(PREFIX)
            .filter { it.lastModified.isBefore(cutoff) }
            .forEach {
                kv.delete(it.key)
                if (!it.key.startsWith(MARKER_PREFIX)) deleted++
            }
        return deleted
    }

    private data class TokenMarker(val tokenHash: String = "")

    private fun docKey(hash: String) = "$PREFIX$hash.json"

    private fun markerKey(userId: UUID, hash: String) = "${userMarkerPrefix(userId)}$hash.json"

    private fun userMarkerPrefix(userId: UUID) = "$MARKER_PREFIX$userId/"

    companion object {
        private const val PREFIX = "refresh-tokens/"
        private const val MARKER_PREFIX = "${PREFIX}by-user/"
    }
}

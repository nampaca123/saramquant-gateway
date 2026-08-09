package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.EmailVerificationDoc
import me.saramquantgateway.infra.storage.s3.S3KvStore
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID

// (purpose, emailHash)당 키 하나 — 덮어쓰기가 곧 "최신 1건 유지"다.
@Component
class EmailVerificationStore(private val kv: S3KvStore) {

    fun findLatest(emailHash: String, purpose: String): EmailVerificationDoc? =
        kv.get(key(emailHash, purpose), EmailVerificationDoc::class.java)

    fun save(doc: EmailVerificationDoc) {
        kv.put(key(doc.emailHash, doc.purpose), doc)
    }

    fun findVerified(id: UUID, emailHash: String, purpose: String): EmailVerificationDoc? =
        findLatest(emailHash, purpose)?.takeIf { it.id == id && it.verified }

    fun deleteExpired(cutoff: Instant): Int {
        val stale = kv.listEntries(PREFIX).filter { it.lastModified.isBefore(cutoff) }
        stale.forEach { kv.delete(it.key) }
        return stale.size
    }

    private fun key(emailHash: String, purpose: String) = "$PREFIX$purpose/$emailHash.json"

    companion object {
        private const val PREFIX = "email-verification/"
    }
}

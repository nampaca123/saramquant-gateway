package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.PortfolioDoc
import me.saramquantgateway.domain.document.PortfolioEntry
import me.saramquantgateway.infra.storage.s3.S3KvStore
import org.springframework.stereotype.Component
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.abs

// portfolios/{userId}.json 한 문서를 읽고-수정-저장한다. 포트폴리오 id는 결정적으로 채번한다.
@Component
class PortfolioStore(private val kv: S3KvStore) {

    fun findByUserId(userId: UUID): PortfolioDoc? = kv.get(docKey(userId), PortfolioDoc::class.java)

    fun save(doc: PortfolioDoc) {
        kv.put(docKey(doc.userId), doc)
    }

    fun findEntry(userId: UUID, portfolioId: Long): PortfolioEntry? =
        findByUserId(userId)?.portfolios?.firstOrNull { it.id == portfolioId }

    private fun docKey(userId: UUID) = "portfolios/$userId.json"

    companion object {
        fun portfolioIdOf(userId: UUID, marketGroup: String): Long {
            val digest = MessageDigest.getInstance("SHA-256").digest("$userId:$marketGroup".toByteArray())
            val raw = ByteBuffer.wrap(digest, 0, 8).long
            return if (raw == Long.MIN_VALUE) Long.MAX_VALUE else abs(raw)
        }
    }
}

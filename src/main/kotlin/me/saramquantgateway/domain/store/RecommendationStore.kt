package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.RecommendationDoc
import me.saramquantgateway.infra.storage.s3.S3KvStore
import org.springframework.stereotype.Component
import java.util.UUID

// recommendations/{userId}/{marketGroup}/{역순 타임스탬프}.json — 키 정렬만으로 최신순이 된다.
@Component
class RecommendationStore(private val kv: S3KvStore) {

    fun save(doc: RecommendationDoc) {
        val reversed = Long.MAX_VALUE - doc.createdAt.toEpochMilli()
        kv.put(prefix(doc.userId, doc.marketGroup) + "%019d.json".format(reversed), doc)
    }

    fun findPage(userId: UUID, marketGroup: String, page: Int, size: Int): PageResult<RecommendationDoc> {
        val keys = kv.listKeys(prefix(userId, marketGroup)).sorted()
        val from = (page.toLong() * size).coerceAtMost(keys.size.toLong()).toInt()
        val to = (from.toLong() + size).coerceAtMost(keys.size.toLong()).toInt()
        val content = keys.subList(from, to).mapNotNull { kv.get(it, RecommendationDoc::class.java) }
        return PageResult(content, keys.size.toLong(), to < keys.size)
    }

    private fun prefix(userId: UUID, marketGroup: String): String = "recommendations/$userId/$marketGroup/"
}

package me.saramquantgateway.domain.store

import me.saramquantgateway.domain.document.LlmAnalysisDoc
import me.saramquantgateway.infra.storage.s3.S3KvStore
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDate

// llm-cache/{stock|portfolio}/{targetId}-{date}-{preset}-{lang}.json 캐시.
@Component
class LlmCacheStore(private val kv: S3KvStore) {

    fun findStock(stockId: Long, date: LocalDate, preset: String, lang: String): LlmAnalysisDoc? =
        kv.get(key(STOCK, stockId, date, preset, lang), LlmAnalysisDoc::class.java)

    fun saveStock(doc: LlmAnalysisDoc) {
        kv.put(key(STOCK, doc.targetId, doc.date, doc.preset, doc.lang), doc)
    }

    fun findPortfolio(portfolioId: Long, date: LocalDate, preset: String, lang: String): LlmAnalysisDoc? =
        kv.get(key(PORTFOLIO, portfolioId, date, preset, lang), LlmAnalysisDoc::class.java)

    fun savePortfolio(doc: LlmAnalysisDoc) {
        kv.put(key(PORTFOLIO, doc.targetId, doc.date, doc.preset, doc.lang), doc)
    }

    fun listPortfolioHistory(portfolioId: Long): List<LlmAnalysisDoc> =
        kv.listKeys(targetPrefix(PORTFOLIO, portfolioId))
            .mapNotNull { kv.get(it, LlmAnalysisDoc::class.java) }
            .sortedByDescending { it.createdAt }

    fun deletePortfolioAll(portfolioId: Long) {
        kv.listKeys(targetPrefix(PORTFOLIO, portfolioId)).forEach { kv.delete(it) }
    }

    fun deleteOlderThan(cutoff: Instant): Int {
        val stale = kv.listEntries(ROOT).filter { it.lastModified.isBefore(cutoff) }
        stale.forEach { kv.delete(it.key) }
        return stale.size
    }

    private fun key(kind: String, targetId: Long, date: LocalDate, preset: String, lang: String): String =
        "$ROOT$kind/$targetId-$date-$preset-$lang.json"

    private fun targetPrefix(kind: String, targetId: Long): String = "$ROOT$kind/$targetId-"

    companion object {
        private const val ROOT = "llm-cache/"
        private const val STOCK = "stock"
        private const val PORTFOLIO = "portfolio"
    }
}

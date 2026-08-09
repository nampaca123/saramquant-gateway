package me.saramquantgateway.feature.llm.service

import me.saramquantgateway.domain.store.LlmCacheStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class LlmCacheCleanupScheduler(private val cacheStore: LlmCacheStore) {

    private val log = LoggerFactory.getLogger(javaClass)

    // 실행 1건당 구조화 로그 1줄을 try/finally로 남긴다.
    @Scheduled(cron = "0 0 3 * * *")
    fun cleanup() {
        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        var deleted = 0
        var status = "error"
        try {
            deleted = cacheStore.deleteOlderThan(Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS))
            status = "ok"
        } finally {
            log.info(
                """{"event":"llm_cache_cleanup","run_id":"%s","status":"%s","deleted":%d,"duration_ms":%d}"""
                    .format(runId, status, deleted, System.currentTimeMillis() - startedAt),
            )
        }
    }

    companion object {
        private const val RETENTION_DAYS = 30L
    }
}

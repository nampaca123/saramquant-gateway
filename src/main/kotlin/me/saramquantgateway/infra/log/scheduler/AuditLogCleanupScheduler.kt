package me.saramquantgateway.infra.log.scheduler

import me.saramquantgateway.domain.store.AuditLogStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Component
class AuditLogCleanupScheduler(private val auditLogStore: AuditLogStore) {

    private val log = LoggerFactory.getLogger(javaClass)

    // 실행 1건당 구조화 로그 1줄을 try/finally로 남긴다.
    @Scheduled(cron = "0 30 3 * * *")
    fun cleanup() {
        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        var deleted = 0
        var status = "error"
        try {
            deleted = auditLogStore.deleteOlderThan(LocalDate.now(ZoneOffset.UTC).minusDays(RETENTION_DAYS))
            status = "ok"
        } finally {
            log.info(
                """{"event":"audit_log_cleanup","run_id":"%s","status":"%s","deleted":%d,"duration_ms":%d}"""
                    .format(runId, status, deleted, System.currentTimeMillis() - startedAt),
            )
        }
    }

    companion object {
        private const val RETENTION_DAYS = 180L
    }
}

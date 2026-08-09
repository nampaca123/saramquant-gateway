package me.saramquantgateway.infra.systememail.scheduler

import me.saramquantgateway.domain.store.EmailVerificationStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

@Component
class VerificationCodeCleanupScheduler(
    private val store: EmailVerificationStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    // 실행 1건당 구조화 로그 1줄을 try/finally로 남긴다.
    @Scheduled(cron = "0 0 3 * * *")
    fun cleanupExpiredCodes() {
        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        var deleted = 0
        var status = "error"
        try {
            deleted = store.deleteExpired(Instant.now().minus(24, ChronoUnit.HOURS))
            status = "ok"
        } finally {
            log.info(
                """{"event":"verification_code_cleanup","run_id":"%s","status":"%s","deleted":%d,"duration_ms":%d}"""
                    .format(runId, status, deleted, System.currentTimeMillis() - startedAt),
            )
        }
    }
}

package me.saramquantgateway.infra.systememail.scheduler

import me.saramquantgateway.domain.store.EmailVerificationStore
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class VerificationCodeCleanupScheduler(
    private val store: EmailVerificationStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *")
    fun cleanupExpiredCodes() {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        val deleted = store.deleteExpired(cutoff)
        log.info("[VerificationCleanup] Deleted {} codes untouched before {}", deleted, cutoff)
    }
}

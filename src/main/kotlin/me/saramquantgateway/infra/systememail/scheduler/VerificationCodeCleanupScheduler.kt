package me.saramquantgateway.infra.systememail.scheduler

import me.saramquantgateway.infra.systememail.repository.EmailVerificationCodeRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class VerificationCodeCleanupScheduler(
    private val repo: EmailVerificationCodeRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    fun cleanupExpiredCodes() {
        val cutoff = Instant.now().minus(24, ChronoUnit.HOURS)
        repo.deleteByExpiresAtBefore(cutoff)
        log.info("[VerificationCleanup] Deleted codes expired before {}", cutoff)
    }
}

package me.saramquantgateway.infra.systememail.service

import me.saramquantgateway.infra.systememail.entity.EmailVerificationCode
import me.saramquantgateway.infra.systememail.enum.VerificationPurpose
import me.saramquantgateway.infra.systememail.repository.EmailVerificationCodeRepository
import me.saramquantgateway.infra.systememail.util.EmailTemplateRenderer
import me.saramquantgateway.infra.aws.lib.AwsSesClient
import me.saramquantgateway.infra.security.crypto.Hasher
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID

@Service
class EmailVerificationService(
    private val repo: EmailVerificationCodeRepository,
    private val hasher: Hasher,
    private val renderer: EmailTemplateRenderer,
    private val sesClient: AwsSesClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val random = SecureRandom()

    companion object {
        private val CODE_TTL = Duration.ofHours(1)
        private val RESEND_COOLDOWN = Duration.ofMinutes(1)
        private val VERIFIED_WINDOW = Duration.ofMinutes(10)
        private const val MAX_ATTEMPTS = 5
    }

    @Transactional
    fun sendCode(email: String, purpose: VerificationPurpose) {
        val emailHash = hasher.hash(email)
        val purposeStr = purpose.name

        val latest = repo.findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(emailHash, purposeStr)

        if (latest != null && !latest.verified && latest.expiresAt.isAfter(Instant.now())) {
            latest.expiresAt = Instant.now()
            repo.save(latest)
        }

        if (latest != null && latest.createdAt.plus(RESEND_COOLDOWN).isAfter(Instant.now())) {
            throw RateLimitedException()
        }

        val code = "%05d".format(random.nextInt(100_000))
        repo.save(
            EmailVerificationCode(
                emailHash = emailHash,
                purpose = purposeStr,
                code = code,
                expiresAt = Instant.now().plus(CODE_TTL),
            )
        )

        val (template, subject) = when (purpose) {
            VerificationPurpose.SIGNUP -> "email-verification" to "Your SaramQuant verification code"
            VerificationPurpose.PASSWORD_RESET -> "password-reset" to "Reset your SaramQuant password"
        }

        try {
            val vars = mutableMapOf<String, Any>("code" to code, "expiresInMinutes" to 60, "email" to email)
            val html = renderer.render(template, vars)
            sesClient.sendHtmlEmail(email, subject, html)
            log.info("[Verification] {} code sent to {}", purpose, email)
        } catch (e: Exception) {
            log.warn("[Verification] Failed to send {} code to {}: {}", purpose, email, e.message)
        }
    }

    fun sendCodeForPasswordReset(email: String, name: String) {
        val emailHash = hasher.hash(email)
        val purposeStr = VerificationPurpose.PASSWORD_RESET.name

        val latest = repo.findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(emailHash, purposeStr)

        if (latest != null && !latest.verified && latest.expiresAt.isAfter(Instant.now())) {
            latest.expiresAt = Instant.now()
            repo.save(latest)
        }

        if (latest != null && latest.createdAt.plus(RESEND_COOLDOWN).isAfter(Instant.now())) {
            throw RateLimitedException()
        }

        val code = "%05d".format(random.nextInt(100_000))
        repo.save(
            EmailVerificationCode(
                emailHash = emailHash,
                purpose = purposeStr,
                code = code,
                expiresAt = Instant.now().plus(CODE_TTL),
            )
        )

        try {
            val vars = mapOf<String, Any>("code" to code, "expiresInMinutes" to 60, "email" to email, "name" to name)
            val html = renderer.render("password-reset", vars)
            sesClient.sendHtmlEmail(email, "Reset your SaramQuant password", html)
            log.info("[Verification] PASSWORD_RESET code sent to {}", email)
        } catch (e: Exception) {
            log.warn("[Verification] Failed to send PASSWORD_RESET code to {}: {}", email, e.message)
        }
    }

    @Transactional
    fun verify(email: String, purpose: VerificationPurpose, code: String): UUID {
        val emailHash = hasher.hash(email)
        val record = repo.findFirstByEmailHashAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(emailHash, purpose.name)
            ?: throw InvalidCodeException()

        if (record.attempts >= MAX_ATTEMPTS) {
            record.expiresAt = Instant.now()
            repo.save(record)
            throw TooManyAttemptsException()
        }

        if (record.expiresAt.isBefore(Instant.now())) throw CodeExpiredException()

        if (record.code != code) {
            record.attempts += 1
            repo.save(record)
            throw InvalidCodeException()
        }

        record.verified = true
        record.verifiedAt = Instant.now()
        repo.save(record)
        return record.id
    }

    fun assertVerified(email: String, purpose: VerificationPurpose, verificationId: UUID) {
        val emailHash = hasher.hash(email)
        val record = repo.findByIdAndEmailHashAndPurposeAndVerifiedTrue(verificationId, emailHash, purpose.name)
            ?: throw EmailNotVerifiedException()

        val verifiedAt = record.verifiedAt ?: throw EmailNotVerifiedException()
        if (verifiedAt.plus(VERIFIED_WINDOW).isBefore(Instant.now())) throw EmailNotVerifiedException()
    }

    class RateLimitedException : RuntimeException("Rate limited")
    class InvalidCodeException : RuntimeException("Invalid code")
    class CodeExpiredException : RuntimeException("Code expired")
    class TooManyAttemptsException : RuntimeException("Too many attempts")
    class EmailNotVerifiedException : RuntimeException("Email not verified")
}

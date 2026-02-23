package me.saramquantgateway.feature.systememail.service

import com.fasterxml.jackson.databind.ObjectMapper
import me.saramquantgateway.domain.entity.user.User
import me.saramquantgateway.feature.systememail.util.EmailTemplateRenderer
import me.saramquantgateway.infra.aws.lib.AwsSesClient
import me.saramquantgateway.infra.log.entity.AuditLog
import me.saramquantgateway.infra.log.repository.AuditLogRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

@Service
class SystemEmailService(
    private val sesClient: AwsSesClient,
    private val renderer: EmailTemplateRenderer,
    private val auditLogRepo: AuditLogRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)
        private val DATETIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC)
    }

    @Async
    fun sendWelcomeEmail(user: User) {
        send(
            to = user.email,
            subject = "Welcome to SaramQuant — investing starts simple",
            template = "welcome",
            variables = mapOf(
                "name" to user.name,
                "email" to user.email,
                "createdAt" to DATE_FMT.format(user.createdAt),
            ),
            emailType = "WELCOME",
            userId = user.id,
        )
    }

    @Async
    fun sendDeactivationEmail(user: User) {
        send(
            to = user.email,
            subject = "Your SaramQuant account has been deactivated",
            template = "account-deactivated",
            variables = mapOf(
                "name" to user.name,
                "email" to user.email,
                "deactivatedAt" to DATETIME_FMT.format(user.deactivatedAt ?: Instant.now()),
            ),
            emailType = "DEACTIVATION",
            userId = user.id,
        )
    }

    @Async
    fun sendReactivationEmail(user: User) {
        send(
            to = user.email,
            subject = "Welcome back to SaramQuant",
            template = "account-reactivated",
            variables = mapOf(
                "name" to user.name,
                "email" to user.email,
                "originalJoinDate" to DATE_FMT.format(user.createdAt),
                "reactivatedAt" to DATETIME_FMT.format(user.lastLoginAt),
            ),
            emailType = "REACTIVATION",
            userId = user.id,
        )
    }

    private fun send(
        to: String,
        subject: String,
        template: String,
        variables: Map<String, Any>,
        emailType: String,
        userId: java.util.UUID,
    ) {
        try {
            val html = renderer.render(template, variables)
            sesClient.sendHtmlEmail(to, subject, html)
            recordAudit(emailType, to, userId)
            log.info("[SystemEmail] {} sent to {}", emailType, to)
        } catch (e: Exception) {
            log.warn("[SystemEmail] Failed to send {} to {}: {}", emailType, to, e.message)
        }
    }

    private fun recordAudit(emailType: String, recipient: String, userId: java.util.UUID) {
        try {
            val metadata = objectMapper.writeValueAsString(mapOf("type" to emailType, "recipient" to recipient))
            auditLogRepo.save(
                AuditLog(server = "gateway", action = "EMAIL", userId = userId, metadata = metadata)
            )
        } catch (e: Exception) {
            log.warn("[SystemEmail] Audit log failed for {}: {}", emailType, e.message)
        }
    }
}

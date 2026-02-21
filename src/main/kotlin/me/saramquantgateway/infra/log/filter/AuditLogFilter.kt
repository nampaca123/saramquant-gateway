package me.saramquantgateway.infra.log.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.saramquantgateway.infra.log.entity.AuditLog
import me.saramquantgateway.infra.log.repository.AuditLogRepository
import me.saramquantgateway.infra.log.service.IpGeolocationService
import me.saramquantgateway.infra.log.util.ClientIpExtractor
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

data class AuditEvent(
    val ip: String,
    val method: String,
    val path: String,
    val userId: UUID?,
    val statusCode: Int,
    val durationMs: Long,
)

@Component
class AuditLogFilter(
    private val eventPublisher: ApplicationEventPublisher,
) : OncePerRequestFilter() {

    override fun shouldNotFilter(request: HttpServletRequest): Boolean {
        val path = request.servletPath
        return path.startsWith("/actuator") || path == "/favicon.ico"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
    ) {
        val start = System.currentTimeMillis()
        chain.doFilter(request, response)
        val duration = System.currentTimeMillis() - start

        val userId = SecurityContextHolder.getContext().authentication?.principal
            ?.let { runCatching { UUID.fromString(it as String) }.getOrNull() }

        try {
            eventPublisher.publishEvent(
                AuditEvent(
                    ip = ClientIpExtractor.extract(request),
                    method = request.method,
                    path = request.servletPath,
                    userId = userId,
                    statusCode = response.status,
                    durationMs = duration,
                )
            )
        } catch (_: Exception) { }
    }
}

@Component
class AuditEventListener(
    private val auditLogRepo: AuditLogRepository,
    private val ipGeoService: IpGeolocationService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handle(event: AuditEvent) {
        try {
            val geoId = ipGeoService.resolveId(event.ip)
            val saved = auditLogRepo.save(
                AuditLog(
                    server = "gateway",
                    action = "API",
                    method = event.method,
                    path = event.path,
                    ipGeolocationId = geoId,
                    userId = event.userId,
                    statusCode = event.statusCode,
                    durationMs = event.durationMs,
                )
            )
            ipGeoService.resolveAndBackfill(event.ip, saved.id)
        } catch (e: Exception) {
            log.warn("[AuditLog] failed to record: {}", e.message)
        }
    }
}

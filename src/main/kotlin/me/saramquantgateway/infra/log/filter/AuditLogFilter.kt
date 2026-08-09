package me.saramquantgateway.infra.log.filter

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import me.saramquantgateway.domain.document.AuditLogDoc
import me.saramquantgateway.domain.store.AuditLogStore
import me.saramquantgateway.infra.log.util.ClientIpExtractor
import me.saramquantgateway.infra.log.util.IpMasker
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
    private val auditLogStore: AuditLogStore,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Async
    @EventListener
    fun handle(event: AuditEvent) {
        try {
            auditLogStore.append(
                AuditLogDoc(
                    server = "gateway",
                    action = "API",
                    method = event.method,
                    path = event.path,
                    ipMasked = event.ip.takeIf { it.isNotBlank() && it != "unknown" }?.let(IpMasker::mask),
                    userId = event.userId,
                    statusCode = event.statusCode,
                    durationMs = event.durationMs,
                )
            )
        } catch (e: Exception) {
            log.warn("[AuditLog] failed to record: {}", e.message)
        }
    }
}

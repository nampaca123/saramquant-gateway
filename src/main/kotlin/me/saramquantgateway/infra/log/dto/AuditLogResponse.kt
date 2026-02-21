package me.saramquantgateway.infra.log.dto

import me.saramquantgateway.infra.log.entity.AuditLog
import java.time.Instant
import java.util.UUID

data class AuditLogResponse(
    val id: UUID,
    val server: String,
    val action: String,
    val method: String?,
    val path: String?,
    val ipGeolocationId: UUID?,
    val userId: UUID?,
    val statusCode: Int?,
    val durationMs: Long?,
    val metadata: String?,
    val createdAt: Instant,
) {
    companion object {
        fun from(e: AuditLog) = AuditLogResponse(
            id = e.id,
            server = e.server,
            action = e.action,
            method = e.method,
            path = e.path,
            ipGeolocationId = e.ipGeolocationId,
            userId = e.userId,
            statusCode = e.statusCode,
            durationMs = e.durationMs,
            metadata = e.metadata,
            createdAt = e.createdAt,
        )
    }
}

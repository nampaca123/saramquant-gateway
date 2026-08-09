package me.saramquantgateway.infra.log.dto

import me.saramquantgateway.domain.document.AuditLogDoc
import java.time.Instant
import java.util.UUID

data class AuditLogResponse(
    val id: UUID,
    val server: String,
    val action: String,
    val method: String?,
    val path: String?,
    val ipGeolocationId: UUID?,
    val ipMasked: String?,
    val userId: UUID?,
    val statusCode: Int?,
    val durationMs: Long?,
    val metadata: String?,
    val createdAt: Instant,
) {
    companion object {
        // ipGeolocationId는 geolocation 제거 후에도 응답 형태 유지를 위해 null로 고정한다.
        fun from(doc: AuditLogDoc) = AuditLogResponse(
            id = doc.id,
            server = doc.server,
            action = doc.action,
            method = doc.method,
            path = doc.path,
            ipGeolocationId = null,
            ipMasked = doc.ipMasked,
            userId = doc.userId,
            statusCode = doc.statusCode,
            durationMs = doc.durationMs,
            metadata = doc.metadata,
            createdAt = doc.createdAt,
        )
    }
}

package me.saramquantgateway.domain.document

import java.time.Instant
import java.util.UUID

data class AuditLogDoc(
    val id: UUID = UUID.randomUUID(),
    val server: String,
    val action: String,
    val method: String? = null,
    val path: String? = null,
    val ipMasked: String? = null,
    val userId: UUID? = null,
    val statusCode: Int? = null,
    val durationMs: Long? = null,
    val metadata: String? = null,
    val createdAt: Instant = Instant.now(),
)

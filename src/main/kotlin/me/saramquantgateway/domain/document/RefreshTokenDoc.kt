package me.saramquantgateway.domain.document

import java.time.Instant
import java.util.UUID

data class RefreshTokenDoc(
    val id: UUID = UUID.randomUUID(),
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant,
    val createdAt: Instant = Instant.now(),
    var revokedAt: Instant? = null,
)

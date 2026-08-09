package me.saramquantgateway.domain.document

import java.time.Instant
import java.util.UUID

data class EmailVerificationDoc(
    val id: UUID = UUID.randomUUID(),
    val emailHash: String,
    val purpose: String,
    val code: String,
    var attempts: Int = 0,
    var expiresAt: Instant,
    var verified: Boolean = false,
    var verifiedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
)

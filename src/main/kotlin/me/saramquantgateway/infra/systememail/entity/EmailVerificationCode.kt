package me.saramquantgateway.infra.systememail.entity

import jakarta.persistence.*
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "email_verification_codes")
class EmailVerificationCode(
    @Id
    @get:JvmName("_getId")
    val id: UUID = UUID.randomUUID(),

    @Column(name = "email_hash", nullable = false, length = 64)
    val emailHash: String,

    @Column(nullable = false, length = 20)
    val purpose: String,

    @Column(nullable = false, length = 5)
    val code: String,

    @Column(nullable = false)
    var attempts: Int = 0,

    @Column(name = "expires_at", nullable = false)
    var expiresAt: Instant,

    @Column(nullable = false)
    var verified: Boolean = false,

    @Column(name = "verified_at")
    var verifiedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),
) : Persistable<UUID> {

    @Transient
    private var _isNew: Boolean = true

    override fun getId(): UUID = id
    override fun isNew(): Boolean = _isNew

    @PostPersist
    @PostLoad
    fun markNotNew() { _isNew = false }
}

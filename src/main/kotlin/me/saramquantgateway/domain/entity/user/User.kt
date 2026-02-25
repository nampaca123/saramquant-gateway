package me.saramquantgateway.domain.entity.user

import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.domain.enum.user.UserRole
import me.saramquantgateway.infra.security.crypto.EncryptionConverter
import jakarta.persistence.*
import org.springframework.data.domain.Persistable
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    @get:JvmName("_getId")
    val id: UUID = UUID.randomUUID(),

    @Convert(converter = EncryptionConverter::class)
    @Column(nullable = false, length = 512)
    val email: String,

    @Column(name = "email_hash", unique = true)
    val emailHash: String? = null,

    @Convert(converter = EncryptionConverter::class)
    @Column(nullable = false, length = 512)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "auth_provider_type")
    val provider: AuthProvider,

    @Convert(converter = EncryptionConverter::class)
    @Column(name = "provider_id", nullable = false, length = 512)
    val providerId: String,

    @Column(name = "password_hash", length = 60)
    var passwordHash: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "user_role_type")
    val role: UserRole = UserRole.STANDARD,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "deactivated_at")
    var deactivatedAt: Instant? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_login_at", nullable = false)
    var lastLoginAt: Instant = Instant.now(),
) : Persistable<UUID> {

    @Transient
    private var _isNew: Boolean = true

    override fun getId(): UUID = id
    override fun isNew(): Boolean = _isNew

    @PostPersist
    @PostLoad
    fun markNotNew() { _isNew = false }
}

package me.saramquantgateway.domain.entity.user

import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.domain.enum.user.UserRole
import jakarta.persistence.*
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(nullable = false, unique = true, length = 255)
    val email: String,

    @Column(nullable = false, length = 100)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "auth_provider_type")
    val provider: AuthProvider,

    @Column(name = "provider_id", nullable = false, length = 255)
    val providerId: String,

    @Column(name = "password_hash", length = 60)
    val passwordHash: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "user_role_type")
    val role: UserRole = UserRole.STANDARD,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant = Instant.now(),

    @Column(name = "last_login_at", nullable = false)
    var lastLoginAt: Instant = Instant.now(),
)

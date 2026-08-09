package me.saramquantgateway.domain.document

import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.domain.enum.stock.Market
import me.saramquantgateway.domain.enum.user.Gender
import me.saramquantgateway.domain.enum.user.InvestmentExperience
import me.saramquantgateway.domain.enum.user.UserRole
import java.time.Instant
import java.util.UUID

// email/name/providerId/nickname은 UserStore가 AES로 암·복호화한다.
data class UserDoc(
    val id: UUID = UUID.randomUUID(),
    val email: String,
    val emailHash: String,
    val name: String,
    val provider: AuthProvider,
    val providerId: String,
    var passwordHash: String? = null,
    val role: UserRole = UserRole.STANDARD,
    var isActive: Boolean = true,
    var deactivatedAt: Instant? = null,
    val createdAt: Instant = Instant.now(),
    var lastLoginAt: Instant = Instant.now(),
    var nickname: String? = null,
    var birthYear: Int? = null,
    var gender: Gender? = null,
    var profileImageKey: String? = null,
    var investmentExperience: InvestmentExperience = InvestmentExperience.BEGINNER,
    var preferredMarkets: MutableSet<Market> = mutableSetOf(),
    var updatedAt: Instant = Instant.now(),
)

data class EmailPointerDoc(val userId: UUID = UUID.randomUUID())

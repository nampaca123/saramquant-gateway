package me.saramquantgateway.infra.user.service

import me.saramquantgateway.domain.entity.user.User
import me.saramquantgateway.domain.entity.user.UserProfile
import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.domain.repository.user.UserProfileRepository
import me.saramquantgateway.domain.repository.user.UserRepository
import me.saramquantgateway.infra.oauth.dto.OAuthUserInfo
import me.saramquantgateway.infra.security.crypto.Hasher
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepo: UserRepository,
    private val profileRepo: UserProfileRepository,
    private val hasher: Hasher,
) {

    fun findByEmail(email: String): User? = userRepo.findByEmailHash(hasher.hash(email))

    fun findActiveByEmail(email: String): User? = userRepo.findByEmailHashAndIsActiveTrue(hasher.hash(email))

    fun findById(id: UUID): User? = userRepo.findById(id).orElse(null)

    @Transactional
    fun createOAuthUser(info: OAuthUserInfo, provider: AuthProvider): User {
        val user = userRepo.save(
            User(
                email = info.email,
                emailHash = hasher.hash(info.email),
                name = info.name,
                provider = provider,
                providerId = info.providerId,
            )
        )
        profileRepo.save(UserProfile(userId = user.id))
        return user
    }

    @Transactional
    fun createManualUser(email: String, name: String, passwordHash: String): User {
        val user = userRepo.save(
            User(
                email = email,
                emailHash = hasher.hash(email),
                name = name,
                provider = AuthProvider.MANUAL,
                providerId = email,
                passwordHash = passwordHash,
            )
        )
        profileRepo.save(UserProfile(userId = user.id))
        return user
    }

    @Transactional
    fun updateLastLogin(userId: UUID) {
        userRepo.findById(userId).ifPresent {
            it.lastLoginAt = Instant.now()
            userRepo.save(it)
        }
    }

    @Transactional
    fun deactivateUser(userId: UUID) {
        userRepo.findById(userId).ifPresent {
            it.isActive = false
            it.deactivatedAt = Instant.now()
            userRepo.save(it)
        }
    }

    @Transactional
    fun reactivateUser(user: User) {
        user.isActive = true
        user.deactivatedAt = null
        user.lastLoginAt = Instant.now()
        userRepo.save(user)
    }
}

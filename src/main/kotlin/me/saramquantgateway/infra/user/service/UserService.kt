package me.saramquantgateway.infra.user.service

import me.saramquantgateway.domain.entity.User
import me.saramquantgateway.domain.entity.UserProfile
import me.saramquantgateway.domain.enum.OAuthProvider
import me.saramquantgateway.domain.repository.UserProfileRepository
import me.saramquantgateway.domain.repository.UserRepository
import me.saramquantgateway.infra.oauth.dto.OAuthUserInfo
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userRepo: UserRepository,
    private val profileRepo: UserProfileRepository,
) {

    fun findByEmail(email: String): User? = userRepo.findByEmail(email)

    fun findById(id: UUID): User? = userRepo.findById(id).orElse(null)

    @Transactional
    fun createUser(info: OAuthUserInfo, provider: OAuthProvider): User {
        val user = userRepo.save(
            User(
                email = info.email,
                name = info.name,
                provider = provider,
                providerId = info.providerId,
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
    fun deleteUser(userId: UUID) {
        userRepo.deleteById(userId)
    }
}

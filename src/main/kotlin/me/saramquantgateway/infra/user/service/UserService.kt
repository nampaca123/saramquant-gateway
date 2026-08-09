package me.saramquantgateway.infra.user.service

import me.saramquantgateway.domain.document.UserDoc
import me.saramquantgateway.domain.enum.auth.AuthProvider
import me.saramquantgateway.domain.store.UserStore
import me.saramquantgateway.infra.oauth.dto.OAuthUserInfo
import me.saramquantgateway.infra.security.crypto.Hasher
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

@Service
class UserService(
    private val userStore: UserStore,
    private val hasher: Hasher,
) {

    fun findByEmail(email: String): UserDoc? = userStore.findByEmailHash(hasher.hash(email))

    fun findActiveByEmail(email: String): UserDoc? = findByEmail(email)?.takeIf { it.isActive }

    fun findById(id: UUID): UserDoc? = userStore.findById(id)

    fun createOAuthUser(info: OAuthUserInfo, provider: AuthProvider): UserDoc = create(
        UserDoc(
            email = info.email,
            emailHash = hasher.hash(info.email),
            name = info.name,
            provider = provider,
            providerId = info.providerId,
        )
    )

    fun createManualUser(email: String, name: String, passwordHash: String): UserDoc = create(
        UserDoc(
            email = email,
            emailHash = hasher.hash(email),
            name = name,
            provider = AuthProvider.MANUAL,
            providerId = email,
            passwordHash = passwordHash,
        )
    )

    fun save(user: UserDoc) {
        user.updatedAt = Instant.now()
        userStore.save(user)
    }

    fun updateLastLogin(userId: UUID) {
        userStore.findById(userId)?.let {
            it.lastLoginAt = Instant.now()
            save(it)
        }
    }

    fun deactivateUser(userId: UUID) {
        userStore.findById(userId)?.let {
            it.isActive = false
            it.deactivatedAt = Instant.now()
            save(it)
        }
    }

    fun reactivateUser(user: UserDoc) {
        user.isActive = true
        user.deactivatedAt = null
        user.lastLoginAt = Instant.now()
        save(user)
    }

    private fun create(doc: UserDoc): UserDoc {
        if (!userStore.create(doc)) throw IllegalStateException("Email already reserved by another user")
        return doc
    }
}

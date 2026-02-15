package me.saramquantgateway.infra.jwt.service

import me.saramquantgateway.domain.entity.RefreshToken
import me.saramquantgateway.domain.repository.RefreshTokenRepository
import me.saramquantgateway.infra.jwt.lib.JwtProvider
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenService(
    private val repo: RefreshTokenRepository,
    private val jwtProvider: JwtProvider,
) {
    companion object {
        private const val GRACE_PERIOD_SECONDS = 10L
    }

    @Transactional
    fun save(userId: UUID, rawToken: String) {
        repo.save(
            RefreshToken(
                userId = userId,
                tokenHash = hash(rawToken),
                expiresAt = jwtProvider.validateToken(rawToken)
                    ?.expiration?.toInstant()
                    ?: Instant.now(),
            )
        )
    }

    @Transactional
    fun rotate(rawToken: String): String {
        val claims = jwtProvider.validateToken(rawToken)
            ?: throw InvalidRefreshTokenException()

        val userId = UUID.fromString(claims.subject)
        val existing = repo.findByTokenHash(hash(rawToken))
            ?: throw InvalidRefreshTokenException()

        if (existing.revokedAt != null) {
            val grace = existing.revokedAt!!.plusSeconds(GRACE_PERIOD_SECONDS)
            if (Instant.now().isBefore(grace)) {
                return findLatestActiveToken(userId)
                    ?: issueNew(userId)
            }
            repo.revokeAllByUserId(userId)
            throw TokenReusedException()
        }

        existing.revokedAt = Instant.now()
        repo.save(existing)
        return issueNew(userId)
    }

    @Transactional
    fun revoke(rawToken: String) {
        repo.findByTokenHash(hash(rawToken))?.let {
            if (it.revokedAt == null) {
                it.revokedAt = Instant.now()
                repo.save(it)
            }
        }
    }

    @Transactional
    fun revokeAll(userId: UUID) {
        repo.revokeAllByUserId(userId)
    }

    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    fun cleanupExpired(): Int = repo.deleteExpired()

    private fun issueNew(userId: UUID): String {
        val newToken = jwtProvider.generateRefreshToken(userId)
        save(userId, newToken)
        return newToken
    }

    private fun findLatestActiveToken(userId: UUID): String? = null

    private fun hash(raw: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(raw.toByteArray())
            .joinToString("") { "%02x".format(it) }

    class InvalidRefreshTokenException : RuntimeException("Invalid refresh token")
    class TokenReusedException : RuntimeException("Refresh token reuse detected")
}

package me.saramquantgateway.infra.jwt.service

import me.saramquantgateway.domain.document.RefreshTokenDoc
import me.saramquantgateway.domain.store.RefreshTokenStore
import me.saramquantgateway.infra.jwt.lib.JwtProvider
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID

@Service
class RefreshTokenService(
    private val store: RefreshTokenStore,
    private val jwtProvider: JwtProvider,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val GRACE_PERIOD_SECONDS = 10L
    }

    fun save(userId: UUID, rawToken: String) {
        store.save(
            RefreshTokenDoc(
                userId = userId,
                tokenHash = hash(rawToken),
                expiresAt = jwtProvider.validateToken(rawToken)
                    ?.expiration?.toInstant()
                    ?: Instant.now(),
            )
        )
    }

    fun rotate(rawToken: String): String {
        val claims = jwtProvider.validateToken(rawToken)
            ?: throw InvalidRefreshTokenException()

        val userId = UUID.fromString(claims.subject)
        val existing = store.findByTokenHash(hash(rawToken))
            ?: throw InvalidRefreshTokenException()

        if (existing.revokedAt != null) {
            val grace = existing.revokedAt!!.plusSeconds(GRACE_PERIOD_SECONDS)
            if (Instant.now().isBefore(grace)) {
                return findLatestActiveToken(userId)
                    ?: issueNew(userId)
            }
            store.revokeAllByUserId(userId, Instant.now())
            throw TokenReusedException()
        }

        existing.revokedAt = Instant.now()
        store.save(existing)
        return issueNew(userId)
    }

    fun revoke(rawToken: String) {
        store.findByTokenHash(hash(rawToken))?.let {
            if (it.revokedAt == null) {
                it.revokedAt = Instant.now()
                store.save(it)
            }
        }
    }

    fun revokeAll(userId: UUID) {
        store.revokeAllByUserId(userId, Instant.now())
    }

    // 실행 1건당 구조화 로그 1줄을 try/finally로 남긴다.
    @Scheduled(fixedRate = 3_600_000)
    fun cleanupExpired(): Int {
        val runId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        var deleted = 0
        var status = "error"
        try {
            deleted = store.deleteExpired(Instant.now())
            status = "ok"
            return deleted
        } finally {
            log.info(
                """{"event":"refresh_token_cleanup","run_id":"%s","status":"%s","deleted":%d,"duration_ms":%d}"""
                    .format(runId, status, deleted, System.currentTimeMillis() - startedAt),
            )
        }
    }

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

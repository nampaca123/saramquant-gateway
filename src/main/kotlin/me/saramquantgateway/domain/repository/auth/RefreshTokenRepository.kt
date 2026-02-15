package me.saramquantgateway.domain.repository.auth

import me.saramquantgateway.domain.entity.auth.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import java.time.Instant
import java.util.UUID

interface RefreshTokenRepository : JpaRepository<RefreshToken, UUID> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    @Modifying
    @Query("UPDATE RefreshToken r SET r.revokedAt = :now WHERE r.userId = :userId AND r.revokedAt IS NULL")
    fun revokeAllByUserId(userId: UUID, now: Instant = Instant.now())

    @Modifying
    @Query("DELETE FROM RefreshToken r WHERE r.expiresAt < :now")
    fun deleteExpired(now: Instant = Instant.now()): Int
}

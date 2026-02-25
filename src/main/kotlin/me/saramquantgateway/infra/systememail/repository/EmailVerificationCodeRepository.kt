package me.saramquantgateway.infra.systememail.repository

import me.saramquantgateway.infra.systememail.entity.EmailVerificationCode
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant
import java.util.UUID

interface EmailVerificationCodeRepository : JpaRepository<EmailVerificationCode, UUID> {

    fun findFirstByEmailHashAndPurposeAndVerifiedFalseOrderByCreatedAtDesc(
        emailHash: String,
        purpose: String,
    ): EmailVerificationCode?

    fun findByIdAndEmailHashAndPurposeAndVerifiedTrue(
        id: UUID,
        emailHash: String,
        purpose: String,
    ): EmailVerificationCode?

    fun findFirstByEmailHashAndPurposeOrderByCreatedAtDesc(
        emailHash: String,
        purpose: String,
    ): EmailVerificationCode?

    fun deleteByExpiresAtBefore(cutoff: Instant)
}

package me.saramquantgateway.infra.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.Size
import java.util.UUID

data class ResetPasswordRequest(
    @field:Email val email: String,
    @field:Size(min = 8, max = 100) val newPassword: String,
    val verificationId: UUID,
)
